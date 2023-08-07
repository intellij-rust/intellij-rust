/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineFunction

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.containers.MultiMap
import org.rust.RsBundle
import org.rust.ide.fixes.ChangeToFieldShorthandFix
import org.rust.ide.fixes.deleteUseSpeck
import org.rust.ide.fixes.updateMutable
import org.rust.ide.refactoring.RsInlineUsageViewDescriptor
import org.rust.ide.refactoring.freshenName
import org.rust.ide.refactoring.inlineTypeAlias.fillPathWithActualType
import org.rust.ide.refactoring.inlineValue.replaceWithAddingParentheses
import org.rust.lang.core.dfa.ExitPoint
import org.rust.lang.core.macros.setContext
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.VALUES
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.liveness
import org.rust.openapiext.isUnitTestMode
import org.rust.stdext.mapNotNullToSet

/** `foo()` */
private class FunctionCallUsage(val functionCall: RsCallExpr, reference: PsiReference) : CallUsage(reference)
/** `foo.func()` */
private class MethodCallUsage(val methodCall: RsMethodCall, reference: PsiReference) : CallUsage(reference)
private sealed class CallUsage(reference: PsiReference) : UsageInfo(reference)

/** `use inner::foo;` */
private class UseSpeckUsage(val useSpeck: RsUseSpeck, reference: PsiReference) : UsageInfo(reference)
/** `bar(foo);` */
private class ReferenceUsage(reference: PsiReference) : UsageInfo(reference)

/**
 * Algorithm overview:
 * - Copy function for each invocation
 * - If it is method, we rewrite the call in UFCS, and transform `self` parameter to usual parameter
 * - Match arguments and parameters
 *   - We need to replace usages of each parameter in function body with something
 *   - If argument is simple expression or variable, substitute it directly
 *   - Otherwise add new let-declaration to function call context, and replace with this new variable
 *   - Note about ref: in case `foo(&a);` we may substitute either `&a` or `a` depending on usage,
 *     e.g. `bar(p)` -> `bar(&a)` and `p.bar()` -> `a.bar()`
 * - Substitute function call with function body
 *   - Function call is statement -> replace it with body directly
 *   - Function body is single expression -> replace function call in any context
 *   - Function call is used as variable initialization - replace let declaration if possible
 *   - Common case: replace function call with return value, remaining function body insert to enclosing block
 */
class RsInlineFunctionProcessor(
    private val project: Project,
    private val originalFunction: RsFunction,
    private val reference: RsReference?,
    private val inlineThisOnly: Boolean,
    private val removeDefinition: Boolean,
) : BaseRefactoringProcessor(project) {

    private val factory: RsPsiFactory = RsPsiFactory(project)

    override fun findUsages(): Array<UsageInfo> {
        val usages = if (inlineThisOnly && reference != null) {
            listOf(reference)
        } else {
            val projectScope = GlobalSearchScope.projectScope(project)
            originalFunction.searchReferences(projectScope)
        }
        return usages
            .map(::createUsageInfo)
            // sorting needed to handle nested calls first: `foo(foo())`
            .sortedByDescending { it.element?.startOffset ?: 0 }
            .toTypedArray()
    }

    private fun createUsageInfo(reference: PsiReference): UsageInfo {
        val element = reference.element
        val useSpeck = element.ancestorOrSelf<RsUseSpeck>()
        val functionCall = run {
            val pathExpr = ((element as? RsPath)?.parent as? RsPathExpr) ?: return@run null
            (pathExpr.parent as? RsCallExpr)?.takeIf { it.expr == pathExpr }
        }
        return when {
            element is RsMethodCall && element.parent is RsDotExpr -> MethodCallUsage(element, reference)
            functionCall != null -> FunctionCallUsage(functionCall, reference)
            useSpeck != null -> UseSpeckUsage(useSpeck, reference)
            else -> ReferenceUsage(reference)
        }
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        @Suppress("UnstableApiUsage")
        val conflicts = MultiMap<PsiElement, @DialogMessage String>()
        val usages = refUsages.get()
        for (usage in usages) {
            if (removeDefinition && usage is ReferenceUsage) {
                conflicts.putValue(usage.element, "Cannot inline function reference")
            }
        }
        return showConflicts(conflicts, usages)
    }

    override fun performRefactoring(usages: Array<UsageInfo>) {
        val function = preprocessFunction(originalFunction)
        val handledUsages = usages.filter { usage ->
            handleUsage(usage, function)
        }
        if (removeDefinition && handledUsages.size == usages.size) {
            originalFunction.deleteWithPreviousWhitespace()
        }
    }

    private fun handleUsage(usage: UsageInfo, function: RsFunction): Boolean =
        when (usage) {
            is ReferenceUsage -> false
            is UseSpeckUsage -> {
                deleteUseSpeck(usage.useSpeck)
                true
            }
            is CallUsage -> inlineCallUsage(usage, function)
            else -> false
        }

    private fun inlineCallUsage(usage: CallUsage, function: RsFunction): Boolean {
        val arguments = when (usage) {
            is FunctionCallUsage -> usage.functionCall.valueArgumentList.exprList
            is MethodCallUsage -> getArgumentsOfMethodCall(usage.methodCall, function) ?: return false
        }
        return InlineSingleUsage(arguments, usage, originalFunction, function).invoke()
    }

    // `self.foo(arg1, arg2)` -> listOf(`&self`, `arg1`, `arg2`)
    private fun getArgumentsOfMethodCall(methodCall: RsMethodCall, function: RsFunction): List<RsExpr>? {
        val dotExpr = methodCall.parent as? RsDotExpr ?: return null
        val callee = if (function.isFirstParameterReference()) {
            // https://doc.rust-lang.org/book/ch05-03-method-syntax.html#wheres-the---operator
            factory.createRefExpr(dotExpr.expr)
        } else {
            dotExpr.expr
        }
        return listOf(callee) + methodCall.valueArgumentList.exprList
    }

    private fun RsFunction.isFirstParameterReference(): Boolean {
        val valueParameterList = valueParameterList ?: return false
        val firstParameter = valueParameterList.valueParameterList.firstOrNull() ?: return false
        return (firstParameter.typeReference as? RsRefLikeType)?.isRef == true
    }

    private fun preprocessFunction(originalFunction: RsFunction): RsFunction {
        val function = originalFunction.copy() as RsFunction
        (originalFunction.context as? RsElement?)?.let { function.setContext(it) }
        replaceSelfParameter(function)
        replaceReturnWithTailExpr(function)
        return function
    }

    // `fn foo(&self, a: i32)` to `fn foo(self1: &Self, a: i32)`
    private fun replaceSelfParameter(method: RsFunction) {
        val valueParameterList = method.valueParameterList ?: return
        val selfParameter = valueParameterList.selfParameter ?: return

        val existingNames = method
            .descendantsOfType<RsNameIdentifierOwner>()
            .mapNotNullToSet { it.name }
        val newName = freshenName("self", existingNames)
        selfParameter.renameUsages(newName, method)

        val ref = if (selfParameter.and != null) "&" else ""
        val selfType = selfParameter.typeReference?.text ?: "${ref}Self"
        val newParameter = factory.createMethodParam("$newName: $selfType")
        selfParameter.replace(newParameter)
    }

    private fun replaceReturnWithTailExpr(function: RsFunction) {
        val block = function.block ?: return
        val lastStatement = block.stmtList.lastOrNull() as? RsExprStmt ?: return
        val returnExpr = lastStatement.expr as? RsRetExpr ?: return

        val returnValue = returnExpr.expr ?: return
        lastStatement.semicolon?.delete()
        returnExpr.replace(returnValue)
    }

    override fun getCommandName(): String = RsBundle.message("command.name.inline.function", originalFunction.name?:"")

    override fun createUsageViewDescriptor(usages: Array<UsageInfo>): UsageViewDescriptor =
        RsInlineUsageViewDescriptor(originalFunction, RsBundle.message("list.item.function.to.inline"))

    override fun getElementsToWrite(descriptor: UsageViewDescriptor): Collection<PsiElement> =
        when {
            inlineThisOnly -> listOfNotNull(reference?.element)
            originalFunction.isWritable -> listOfNotNull(reference?.element, originalFunction)
            else -> emptyList()
        }

    companion object {
        fun doesFunctionHaveMultipleReturns(fn: RsFunction): Boolean {
            val entryPoints = mutableListOf<ExitPoint>()
            val sink: (ExitPoint) -> Unit = {
                if (it !is ExitPoint.TryExpr) {
                    entryPoints.add(it)
                }
            }
            ExitPoint.process(fn, sink)
            return entryPoints.count() > 1 && entryPoints.dropLast(1).any { it is ExitPoint.Return }
        }

        fun isFunctionRecursive(fn: RsFunction): Boolean =
            fn.descendantsOfType<RsPath>().any { it.reference?.resolve() == fn }

        fun checkIfLoopCondition(fn: RsFunction, element: PsiElement): Boolean {
            val block = fn.block!!
            val (statements, tailExpr) = block.expandedStmtsAndTailExpr

            val hasStatements = when (tailExpr) {
                null -> statements.size > 1 ||
                    statements.size == 1 && statements[0].descendantsOfType<RsRetExpr>().isEmpty()
                else -> statements.isNotEmpty()
            }

            return hasStatements && element.ancestorOrSelf<RsWhileExpr>() != null
        }
    }
}

private class InlineSingleUsage(
    private val arguments: List<RsExpr>,
    private val usage: CallUsage,
    private val originalFunction: RsFunction,
    function: RsFunction,
) {

    /** The expression which should be replaced by function body */
    private val functionCall: RsExpr = prepareFunctionCall(usage)
    private val factory: RsPsiFactory = RsPsiFactory(functionCall.project)
    private val function: RsFunction = preprocessFunction(function)

    fun invoke(): Boolean {
        val parametersAndArguments = matchParametersAndArguments() ?: return false
        val letDeclarations = substituteParametersInFunctionBody(parametersAndArguments)

        collapseStructLiteralFieldsShorthand()
        replaceSelfWithActualType()
        InsertFunctionBody(functionCall, function, letDeclarations).invoke()
        return true
    }

    private fun matchParametersAndArguments(): List<Pair<RsPat, RsExpr>>? {
        val parameters = function.valueParameterList
            ?.takeIf { it.variadic == null }
            ?.valueParameterList
            ?.map { it.pat ?: return null }
            ?: return null
        if (parameters.size != arguments.size) return null

        return matchPatternsAndExpressions(parameters, arguments)
    }

    private fun substituteParametersInFunctionBody(parametersAndArguments: List<Pair<RsPat, RsExpr>>): List<RsLetDecl> {
        val letDeclarations = mutableListOf<RsLetDecl>()
        for ((parameter, argument) in parametersAndArguments) {
            if (trySubstituteParameterDirectly(parameter, argument)) continue
            letDeclarations += factory.createStatement("let ${parameter.text} = ${argument.text};") as RsLetDecl
        }
        return letDeclarations
    }

    private fun trySubstituteParameterDirectly(parameter: RsPat, argument: RsExpr): Boolean {
        val patBinding = (parameter as? RsPatIdent)?.patBinding ?: return false
        val bindingMode = patBinding.bindingMode
        if (bindingMode?.ref != null) return false
        val parameterIsMut = bindingMode?.mut != null

        val usages = patBinding
            .searchReferences(LocalSearchScope(function))
            .mapNotNull { it.element as? RsElement }
        if (usages.isEmpty() && !argument.hasSideEffects) return true
        if (trySubstituteVariable(usages, parameterIsMut, argument)) return true
        /* TODO: substitute field access `f(s.x)` */
        if (!parameterIsMut && trySubstituteSimpleLiteral(argument, usages)) return true
        return false
    }

    private fun trySubstituteSimpleLiteral(argument: RsExpr, usages: List<RsElement>): Boolean {
        if (!argument.isSimpleLiteral(usages.size == 1)) return false
        val usagesExprs = usages.map { it.parent as? RsPathExpr ?: return false }
        for (usage in usagesExprs) {
            usage.replaceWithAddingParentheses(argument, factory)
        }
        return true
    }

    // `foo(a)` or `foo(&a)` or `foo(&mut a)`
    // `fn foo(p: i32)` or `fn foo(mut p: i32)`
    private fun trySubstituteVariable(
        parameterUsages: List<RsElement>,
        parameterIsMut: Boolean,
        argumentExpr: RsExpr,
    ): Boolean {
        val argument = parseExprAsVariableArgument(argumentExpr) ?: return false
        val hasNameConflict = parameterUsages.any { argument.name in it.getLocalVariableVisibleBindings() }
        if (hasNameConflict) return false

        return when (argument.type) {
            VariableArgumentType.PLAIN -> {
                substituteVariablePassedByValue(argument, parameterUsages, parameterIsMut)
            }
            VariableArgumentType.REF -> {
                if (parameterIsMut) return false
                substituteVariablePassedByReference(argument, argumentExpr, parameterUsages)
            }
        }
    }

    private fun substituteVariablePassedByValue(
        argument: VariableArgumentInfo,
        parameterUsages: List<RsElement>,
        parameterIsMut: Boolean,
    ): Boolean {
        if (parameterIsMut && !tryMakeDeclarationMutable(argument.path)) return false
        for (usage in parameterUsages) {
            when (usage) {
                is RsPath -> usage.replace(argument.path)
                is RsMacroBodyIdent -> {
                    val identifier = factory.createIdentifier(argument.name)
                    usage.identifier.replace(identifier)
                }
                else -> continue
            }
        }
        return true
    }

    private fun substituteVariablePassedByReference(
        argument: VariableArgumentInfo,
        argumentExpr: RsExpr,
        parameterUsages: List<RsElement>,
    ): Boolean {
        val parameterUsagesExprs = parameterUsages.map { it.parent as? RsPathExpr ?: return false }
        for (usage in parameterUsagesExprs) {
            val parent = usage.parent
            // `parameter.method()` or `parameter.field`
            val isMethodOrField = parent is RsDotExpr && parent.expr == usage
            // `parameter()`
            val isCallExpr = parent is RsCallExpr && parent.expr == usage
            when {
                isMethodOrField || isCallExpr -> {
                    usage.path.replace(argument.path)
                }
                // `*(&mut a) = ...`; equivalent to `a = ...;`
                parent is RsUnaryExpr && parent.operatorType == UnaryOperator.DEREF -> {
                    parent.replace(factory.createExpression(argument.name))
                }
                else -> usage.replace(argumentExpr)
            }
        }
        return true
    }

    private enum class VariableArgumentType { PLAIN, REF }
    private data class VariableArgumentInfo(val path: RsPath, val name: String, val type: VariableArgumentType)

    // `func(a);`         RsPathExpr
    // `func(&a);`        RsUnaryExpr -> RsPathExpr
    // `func(&mut a);`    RsUnaryExpr -> RsPathExpr
    private fun parseExprAsVariableArgument(argument: RsExpr): VariableArgumentInfo? {
        val path = (argument.unwrapReference() as? RsPathExpr)?.path ?: return null
        val argumentType = if (argument is RsPathExpr) VariableArgumentType.PLAIN else VariableArgumentType.REF
        if (path.path != null) return null
        val name = path.referenceName ?: return null
        return VariableArgumentInfo(path, name, argumentType)
    }

    // See `test substitute plain variable argument to mut parameter`
    private fun tryMakeDeclarationMutable(argument: RsPath): Boolean {
        val declaration = argument.reference?.resolve() as? RsPatBinding ?: return false
        if (!isLastUsageOfDeclaration(declaration, argument)) return false
        if (declaration.bindingMode?.mut != null) return true
        updateMutable(function.project, declaration)
        return true
    }

    private fun isLastUsageOfDeclaration(declaration: RsPatBinding, usage: RsPath): Boolean {
        val scope = usage.parentOfType<RsFunction>() ?: return false
        if (declaration.parentOfType<RsFunction>() != scope) return false

        val lastUsages = scope.liveness?.lastUsages ?: return false
        val declarationLastUsages = lastUsages[declaration] ?: return false
        return declarationLastUsages.any { it is RsPathExpr && it.path == usage }
    }

    private fun prepareFunctionCall(usage: CallUsage): RsExpr {
        val functionCall = when (usage) {
            is FunctionCallUsage -> usage.functionCall
            is MethodCallUsage -> usage.methodCall.parent as RsDotExpr
        }
        return functionCall.unwrapAwait() ?: functionCall
    }

    // `foo().await` -> `foo()`
    //  ~~~~~ [this]
    private fun RsExpr.unwrapAwait(): RsExpr? {
        if (!originalFunction.isAsync) return null
        val parent = parent as? RsDotExpr
        if (parent?.fieldLookup?.isAsync != true) return null
        return parent.replace(this) as RsExpr
    }

    private fun preprocessFunction(originalFunction: RsFunction): RsFunction {
        val outerScopeNames = functionCall.getAllVisibleBindingsIncludingContainingLetDeclaration()
        val functionNames = function.descendantsOfType<RsNameIdentifierOwner>().mapNotNullTo(hashSetOf()) { it.name }
        val usedNames = (outerScopeNames + functionNames).toHashSet()
        val getFreshName = { name: String ->
            if (name in outerScopeNames) {
                freshenName(name, usedNames).also { usedNames += it }
            } else {
                null
            }
        }

        val function = originalFunction.copy() as RsFunction
        (originalFunction.context as? RsElement?)?.let { function.setContext(it) }
        expandStructLiteralFieldsShorthand(function)
        renameParametersIfNecessary(function, getFreshName)
        renameTopLevelDeclarationsIfNecessary(function, getFreshName)
        return function
    }

    // See `test name conflict of parameter name with outer scope`
    private fun renameParametersIfNecessary(function: RsFunction, getFreshName: (String) -> String?) {
        val parameters = function.valueParameterList ?: return
        for (patIdent in parameters.descendantsOfType<RsPatIdent>()) {
            val patName = patIdent.patBinding.referenceName
            val freshName = getFreshName(patName) ?: continue
            patIdent.patBinding.renameWithUsagesTo(freshName, function)
        }
    }

    private fun renameTopLevelDeclarationsIfNecessary(function: RsFunction, getFreshName: (String) -> String?) {
        val block = function.block ?: return
        val declarationsByName = block.topLevelDeclarations
        for ((name, declarations) in declarationsByName) {
            val freshName = getFreshName(name) ?: continue
            declarations.renameWithUsagesTo(freshName, function)
        }
    }

    private fun RsElement.getAllVisibleBindingsIncludingContainingLetDeclaration(): Set<String> {
        val declarationNames = ancestorStrict<RsLetDecl>()
            ?.pat
            ?.descendantsOfType<RsPatBinding>()
            ?.map { it.referenceName }
            .orEmpty()
        return getAllVisibleBindings() + declarationNames
    }

    // `Foo { field }` to `Foo { field: field }`
    private fun expandStructLiteralFieldsShorthand(function: RsFunction) {
        for (field in function.descendantsOfType<RsStructLiteralField>()) {
            if (field.colon != null) continue
            val name = field.identifier?.text ?: continue
            field.add(factory.createColon())
            field.add(factory.createExpression(name))
        }
    }

    // `Foo { field: field }` to `Foo { field }`
    private fun collapseStructLiteralFieldsShorthand() {
        for (field in function.descendantsOfType<RsStructLiteralField>()) {
            val name = field.identifier?.text ?: continue
            if (field.colon != null && name == field.expr?.text) {
                ChangeToFieldShorthandFix.applyShorthandInit(field)
            }
        }
    }

    /**
     * impl Foo {
     *     fn foo() {
     *         Self::bar();
     *     }   ~~~~ replaces with `Foo::bar();`
     *     ...
     * }
     */
    private fun replaceSelfWithActualType() {
        run {
            // Don't replace `Self` if it can be resolved to same type
            val path = RsCodeFragmentFactory(function.project).createPath("Self::${function.name}", functionCall, ns = VALUES)
            if (path?.reference?.resolve() == originalFunction) return
        }

        val block = function.block ?: return
        val paths = block
            .descendantsOfType<RsPath>()
            .filter { !it.hasColonColon && it.cself != null }
            .ifEmpty { return }

        val impl = function.owner as? RsAbstractableOwner.Impl ?: return
        val typeReference = impl.impl.typeReference ?: return
        val substitution = usage.getSubstitution() ?: return

        for (path in paths) {
            fillPathWithActualType(path, typeReference, substitution)
        }
    }

    private fun CallUsage.getSubstitution(): Substitution? =
        when (this) {
            is FunctionCallUsage -> {
                val path = (functionCall.expr as? RsPathExpr)?.path
                path?.reference?.advancedResolve()?.subst
            }
            is MethodCallUsage -> {
                val methodCall = methodCall
                val inference = methodCall.inference
                inference?.getResolvedMethodSubst(methodCall)
            }
        }
}

private class InsertFunctionBody(
    private val functionCall: RsExpr,
    private val function: RsFunction,
    private val letDeclarations: List<RsLetDecl>,
) {

    private val factory: RsPsiFactory = RsPsiFactory(function.project)
    private lateinit var bodyStatements: List<RsElement>

    fun invoke() {
        val functionBody = function.block ?: return
        if (functionBody.lbrace.getNextNonCommentSibling() == functionBody.rbrace) {
            (functionCall.parent as? RsExprStmt)?.delete()
            return
        }

        addLetDeclarationsToFunctionBody()
        bodyStatements = functionBody.stmtsAndMacros.toList()

        invokeIfBodyIsSingleExpression()
            || invokeIfFunctionCallIsStatement()
            || invokeIfFunctionCallUsedInLetDeclaration()
            || invokeGenericCase()
    }

    // `fn func() { 2 + 2 }`
    private fun invokeIfBodyIsSingleExpression(): Boolean {
        if (letDeclarations.isNotEmpty()) return false
        val statement = bodyStatements.singleOrNull() as? RsExprStmt ?: return false
        if (statement.semicolon != null || statement.textContains('\n')) return false

        functionCall
            .replaceWithAddingParentheses(statement.expr, factory)
            .unwrapTryExprIfPossible()
        return true
    }

    private fun addLetDeclarationsToFunctionBody() {
        val block = function.block ?: return
        val firstStatement = block.lbrace.getNextNonCommentSibling() ?: return
        // PSI post formatting sometimes doesn't work properly without it
        val indent = (firstStatement.prevSibling as? PsiWhiteSpace)?.text?.substringAfterLast('\n').orEmpty()
        for (letDeclaration in letDeclarations) {
            block.addBefore(letDeclaration, firstStatement)
            block.addBefore(factory.createWhitespace("\n$indent"), firstStatement)
        }
    }

    // `{ ...; func(); ... }`
    private fun invokeIfFunctionCallIsStatement(): Boolean {
        val callStatement = (functionCall.parent as? RsExprStmt)?.takeIf { it.parent is RsBlock }
            ?: functionCall.wrapInBlockExprIfPossible()
            ?: return false
        addSemicolonToLastStatementIfNeeded(callStatement)
        callStatement.replaceWithRange(bodyStatements.first(), bodyStatements.last())
        return true
    }

    private fun addSemicolonToLastStatementIfNeeded(callStatement: RsStmt) {
        val lastStatement = bodyStatements.last()
        val lastStatementNeedSemicolon = when (lastStatement) {
            is RsStmt -> lastStatement.semicolon == null
            is RsMacroCall -> lastStatement.semicolon == null && lastStatement.bracesKind != MacroBraces.BRACES
            else -> false
        }
        if (function.retType != null && callStatement.semicolon != null && lastStatementNeedSemicolon) {
            lastStatement.add(factory.createSemicolon())
        }
    }

    // `func() { ...; return (c, d); }`
    // `let (a, b) = func();`
    private fun invokeIfFunctionCallUsedInLetDeclaration(): Boolean {
        val returnValue = (bodyStatements.last() as? RsExprStmt)
            ?.takeIf { it.semicolon == null }?.expr
            ?: return false
        val outerDeclaration = functionCall.parent as? RsLetDecl ?: return false
        val renames = findRenames(outerDeclaration, returnValue) ?: return false
        applyRenames(renames)

        outerDeclaration.replaceWithRange(bodyStatements.first(), bodyStatements.dropLast(1).last())
        return true
    }

    private data class Rename(val innerDeclaration: RsPatBinding, val newName: String, val needMutable: Boolean)

    private fun applyRenames(renames: List<Rename>) {
        val innerDeclarations = function.block!!.topLevelDeclarations
        for ((declaration, newName, needMutable) in renames) {
            // fn foo() {
            //     let x = 1;
            //     let x = bar(x);  // this is `declaration`, but we want to rename previous `let x` as well
            //     return x;
            // }
            val declarations = innerDeclarations[declaration.referenceName] ?: listOf(declaration)
            check(declaration in declarations)
            declarations.renameWithUsagesTo(newName, function)

            if (needMutable) {
                updateMutable(function.project, declaration)
            }
        }
    }

    private fun findRenames(letDeclaration: RsLetDecl, returnValue: RsExpr): List<Rename>? {
        val matches = matchPatternAndExpression(letDeclaration.pat ?: return null, returnValue)
        return matches.map { (outerPat, returnExpr) ->
            // in scope of function call
            val outerDeclaration = outerPat as? RsPatIdent ?: return null
            val newName = outerDeclaration.patBinding.referenceName

            // in function body
            val returnPath = (returnExpr as? RsPathExpr)?.path?.takeIf { it.path == null } ?: return null
            val innerDeclaration = returnPath.reference?.resolve() as? RsPatBinding ?: return null
            val innerFullDeclaration = innerDeclaration.containingLetDeclaration() ?: return null
            if (innerFullDeclaration.parent != function.block) return null

            val needMutable = outerDeclaration.patBinding.bindingMode?.mut != null && innerDeclaration.bindingMode?.mut == null
            Rename(innerDeclaration, newName, needMutable)
        }
    }

    private fun invokeGenericCase(): Boolean {
        val tailExpr = when (val lastStmt = bodyStatements.last()) {
            is RsExprStmt -> lastStmt.expr
            is RsMacroCall -> lastStmt
            else -> null
        }
        val (returnValue, lastBodyStatement) = if (tailExpr != null) {
            tailExpr to bodyStatements.dropLast(1).lastOrNull()
        } else {
            factory.createExpression("()") to bodyStatements.last()
        }
        val functionCallReplaced = functionCall
            .replaceWithAddingParentheses(returnValue, factory)
            .unwrapTryExprIfPossible()

        if (lastBodyStatement == null) return true
        val containingStatement = functionCallReplaced.findOrCreateContainingStatement() ?: return false
        containingStatement.parent.addRangeBefore(bodyStatements.first(), lastBodyStatement, containingStatement)
        return true
    }

    // `let _ = || bar(foo());` => `let _ = || { bar(foo()) };`
    //                 ~~~~~ this
    //                                           ~~~~~~~~~~ containingStatement
    private fun PsiElement.findOrCreateContainingStatement(): RsStmt? {
        for (element in ancestors) {
            if (element.parent is RsBlock) return element as? RsStmt
            element.wrapInBlockExprIfPossible()?.let { return it }
        }
        if (isUnitTestMode) error("Can't find containing statement")
        return null
    }

    private fun PsiElement.wrapInBlockExprIfPossible(): RsStmt? {
        val expr = when (val parent = parent) {
            is RsMatchArm -> parent.expr
            is RsLambdaExpr -> parent.expr
            is RsConstant -> parent.expr
            else -> return null
        }
        if (expr != this) return null
        (parent as? RsMatchArm)?.comma?.delete()
        return wrapInBlockExpr()
    }

    private fun PsiElement.wrapInBlockExpr(): RsStmt? {
        val blockExpr0 = RsPsiFactory(project).createBlockExpr("\n$text\n")
        val blockExpr = replace(blockExpr0) as RsBlockExpr
        return blockExpr.block.stmtList.singleOrNull()
    }
}

private fun matchPatternsAndExpressions(patterns: List<RsPat>, expressions: List<RsExpr>): List<Pair<RsPat, RsExpr>> =
    (patterns zip expressions).flatMap { (parameter, argument) ->
        matchPatternAndExpression(parameter, argument)
    }

private fun matchPatternAndExpression(pattern: RsPat, expression: RsExpr): List<Pair<RsPat, RsExpr>> {
    if (pattern is RsPatTup && expression is RsTupleExpr) {
        val patList = pattern.patList
        val exprList = expression.exprList
        if (patList.size == exprList.size) {
            return matchPatternsAndExpressions(patList, exprList)
        }
    }
    return listOf(pattern to expression)
}

private fun RsElement.containingLetDeclaration(): RsLetDecl? =
    when (val parent = parent) {
        is RsLetDecl -> parent
        is RsPat -> parent.containingLetDeclaration()
        else -> null
    }

@Suppress("KotlinConstantConditions")
private fun RsExpr.isSimpleLiteral(singleUse: Boolean): Boolean {
    return when (this) {
        is RsUnaryExpr -> expr?.isSimpleLiteral(singleUse) == true
        is RsLitExpr -> {
            if (singleUse) return true
            when (val kind = kind) {
                is RsLiteralKind.Boolean -> true
                is RsLiteralKind.Integer -> kind.value == 0L
                is RsLiteralKind.String -> kind.rawValue == ""
                else -> false
            }
        }
        is RsBinaryExpr -> singleUse
            && left.isSimpleLiteral(singleUse)
            && right?.isSimpleLiteral(singleUse) == true
        is RsParenExpr -> singleUse && expr?.isSimpleLiteral(singleUse) == true
        is RsUnitExpr -> true
        else -> false
    }
}

private val RsBlock.topLevelDeclarations: Map<String, List<RsPatBinding>>
    get() = childrenOfType<RsLetDecl>()
        .flatMap { it.descendantsOfType<RsPatBinding>() }
        .groupBy { it.referenceName }

private fun List<RsPatBinding>.renameWithUsagesTo(name: String, scope: RsElement) {
    for (declaration in this) {
        declaration.renameUsages(name, scope)
    }
    for (declaration in this) {
        declaration.setName(name)
    }
}

private fun RsPatBinding.renameWithUsagesTo(name: String, scope: RsElement) {
    renameUsages(name, scope)
    setName(name)
}

private fun RsElement.renameUsages(name: String, scope: RsElement) {
    val namePath = RsPsiFactory(scope.project).tryCreatePath(name)!!
    val usages = searchReferences(LocalSearchScope(scope))
    for (usage in usages) {
        usage.element.replace(namePath)
    }
}

private fun PsiElement.replaceWithRange(first: PsiElement, last: PsiElement) {
    parent.addRangeBefore(first, last, this)
    delete()
}

// `Some(0)?` -> `0`
//  ~~~~~~~ [this]
private fun PsiElement.unwrapTryExprIfPossible(): PsiElement {
    val parent = parent as? RsTryExpr ?: return this
    if (this !is RsCallExpr) return this
    val value = valueArgumentList.exprList.singleOrNull() ?: return this
    val path = (expr as? RsPathExpr)?.path ?: return this
    val variant = path.reference?.resolve() as? RsEnumVariant ?: return this
    val target = variant.parentEnum
    val isSome = variant.name == "Some" && target == knownItems.Option
    val isOk = variant.name == "Ok" && target == knownItems.Result
    return if (isSome || isOk) {
        parent.replace(value)
    } else {
        this
    }
}
