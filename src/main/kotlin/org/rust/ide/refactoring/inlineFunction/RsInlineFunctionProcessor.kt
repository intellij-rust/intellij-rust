/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineFunction

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.NotNull
import org.rust.ide.refactoring.RsInlineUsageViewDescriptor
import org.rust.ide.surroundWith.addStatements
import org.rust.lang.core.dfa.ExitPoint
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.impl.RsCallExprImpl
import org.rust.lang.core.resolve.ref.RsReference

class RsInlineFunctionProcessor(
    private val project: Project,
    private val function: RsFunction,
    private val ref: RsReference?,
    private val inlineThisOnly: Boolean,
    private val removeDefinition: Boolean,
    private val factory: RsPsiFactory = RsPsiFactory(project),
    private var usagesAsReference: List<PsiReference> = emptyList()
) : BaseRefactoringProcessor(project) {

    override fun findUsages(): Array<UsageInfo> {
        if (inlineThisOnly && ref != null) {
            return arrayOf(UsageInfo(ref))
        }

        val projectScope = GlobalSearchScope.projectScope(project)
        val usages = mutableListOf<PsiReference>()
        usages.addAll(ReferencesSearch.search(function, projectScope).findAll())

        usagesAsReference = usages.filter {
            it.element.ancestorOrSelf<RsCallExpr>() == null
                && it.element.ancestorOrSelf<RsMethodCall>() == null
        }

        usages.removeAll(usagesAsReference)
        return usages.map(::UsageInfo).toTypedArray()
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        @Suppress("UnstableApiUsage")
        val conflicts = MultiMap<PsiElement, @DialogMessage String>()
        refUsages.get().forEach { usage ->
            val caller = usage.element?.ancestors?.filter { it is RsCallExpr || it is RsDotExpr }?.firstOrNull()
            val exprAncestor = usage.element?.ancestorOrSelf<RsStmt>() ?: usage.element?.ancestorOrSelf<RsExpr>()
            when {
                exprAncestor == null -> {
                    conflicts.putValue(usage.element, "Usage is not part of an expression")
                    return@forEach
                }
                !exprAncestor.isWritable -> {
                    conflicts.putValue(usage.element, "Usage is not writable")
                    return@forEach
                }
                usagesAsReference.contains(usage.reference) && removeDefinition -> {
                    conflicts.putValue(usage.element, "Usage with function pointer inline isn't currently supported")
                    return@forEach
                }
                caller == null -> {
                    conflicts.putValue(usage.element, "Usage is not part of a caller expression")
                    return@forEach
                }
                checkCallerConflicts(function, caller) != null -> {
                    conflicts.putValue(caller, checkCallerConflicts(function, caller))
                    return@forEach
                }
            }
        }

        return showConflicts(conflicts, refUsages.get())
    }

    @Suppress("UnstableApiUsage")
    @DialogMessage
    private fun checkCallerConflicts(function: RsFunction, caller: PsiElement): String? {
        val funcArguments = (function.copy() as RsFunction).valueParameters
        var callArguments = when (caller) {
            is RsCallExpr -> caller.valueArgumentList.exprList
            is RsDotExpr -> {
                val methodCall = caller.methodCall
                    ?: return "Cannot inline field lookup"
                methodCall.valueArgumentList.exprList
            }
            else -> return "Unknown caller expression type"
        }

        if (isDoubleSemicolonMethodCall(function, caller)) {
            callArguments = callArguments.drop(1)
        }
        if (funcArguments.size != callArguments.size) {
            return "Cannot inline function to references with mismatching arguments"
        }
        return null
    }

    private fun isDoubleSemicolonMethodCall(function: RsFunction, caller: PsiElement) =
        function.selfParameter != null && caller is RsCallExpr && caller.firstChild is RsPathExpr

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        usages.asIterable().forEach loop@{
            val reference = it.reference as? RsReference ?: return@loop
            inlineWithLetBindingsAdded(reference, function)
        }
        if (removeDefinition) {
            function.delete()
        }
    }

    override fun getCommandName(): String = "Inline function ${function.declaration}"

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
        return RsInlineUsageViewDescriptor(function, "Function to inline")
    }

    companion object {
        private val LOG: Logger = logger<RsInlineFunctionProcessor>()

        fun doesFunctionHaveMultipleReturns(fn: RsFunction): Boolean {
            val entryPoints =  mutableListOf<ExitPoint>()
            val sink: (ExitPoint) -> Unit = {
                if (it !is ExitPoint.TryExpr) {
                    entryPoints.add(it)
                }
            }
            ExitPoint.process(fn, sink)
            return entryPoints.count() > 1 && entryPoints.dropLast(1).any { it is ExitPoint.Return }
        }

        fun isFunctionRecursive(fn: RsFunction): Boolean {
            return fn.descendantsOfType<RsPath>().any { it.reference?.resolve() == fn }
        }

        fun checkIfLoopCondition(fn: RsFunction, element: PsiElement): Boolean {
            val block = fn.block!!
            val statements = block.stmtList

            val hasStatements = when (block.expr) {
                null -> statements.size > 1 ||
                    statements.size == 1 && statements[0].descendantsOfType<RsRetExpr>().isEmpty()
                else -> statements.size > 0
            }

            return hasStatements && element.ancestorOrSelf<RsWhileExpr>() != null
        }
    }

    private fun inlineWithLetBindingsAdded(ref: RsReference, function: RsFunction) {
        try {
            val functionDup = function.copy() as RsFunction
            val body = functionDup.block ?: throw IncorrectOperationException("Empty function bypassed preprocessing")
            replaceLastExprToStatement(body)
            val enclosingStatement = ref.element.ancestorOrSelf<RsStmt>()
                ?: ref.element.ancestorOrSelf<RsExpr>()
                ?: throw IncorrectOperationException(
                    "Usage without expression parent bypassed preprocessing: ${ref.element.text}")
            val blockParent = enclosingStatement.ancestorOrSelf<RsBlock>()
            val childContainingEnclosingStatement = if (blockParent?.children?.isNotEmpty() == true) {
                blockParent.children.first { it.isAncestorOf(enclosingStatement) }
            } else {
                null
            }

            val caller = ref.element.ancestors.filter { it is RsCallExpr || it is RsDotExpr }.firstOrNull()
                ?: throw IncorrectOperationException(
                    "Usage without caller expression parent bypassed preprocessing: ${ref.element.text}")

            val funcScope = LocalSearchScope(body)

            val selfParam = functionDup.selfParameter
            replaceSelfParamWithExpr(selfParam, caller, funcScope)
            if (removeDefinition) {
                removeFunctionUseStatements()
            }

            replaceParameterUsages(functionDup, caller, funcScope)

            val retExprInside = body.descendantsOfType<RsRetExpr>().firstOrNull()?.expr
            if (retExprInside != null && body.stmtList.size == 1) {
                caller.replace(retExprInside)
                return
            }
            replaceCallerWithRetExpr(body, caller)

            val leftSibling = enclosingStatement.leftSiblings.firstOrNull { it is RsStmt }
            val rightSibling = enclosingStatement.rightSiblings.firstOrNull { it is RsStmt }
            if (blockParent != null) {
                if (enclosingStatement.isPhysical) {
                    body.children.filter { it.text != ";" }.forEach {
                        enclosingStatement.addLeftSibling(it)
                    }
                } else {
                    addFunctionBodyToCallerWithCorrectSpacing(
                        leftSibling,
                        rightSibling,
                        blockParent,
                        body,
                        childContainingEnclosingStatement
                    )
                }

                blockParent.children.filter { it.text == ";" }.forEach { it.delete() }
            }
            enclosingStatement.addLeftSibling(factory.createNewline())

            enclosingStatement.ancestorOrSelf<RsBlock>()?.let {
                it.replace(CodeStyleManager.getInstance(project).reformat(it))
            }

            if (enclosingStatement.descendantsOfType<RsExpr>().isEmpty() || enclosingStatement.text == ";") {
                enclosingStatement.delete()
            }
        } catch (e: IncorrectOperationException) {
            LOG.error(e)
        }
    }

    private fun removeFunctionUseStatements() {
        usagesAsReference.filter {
            it.element.ancestorOrSelf<RsUseSpeck>() != null
        }.forEach {
            val useGroup = getLowestGroupWithMultipleChildren(it)
            val useGroupChildrenSize = useGroup?.useSpeckList?.size

            if (useGroupChildrenSize != null && useGroupChildrenSize > 1) {
                val useSpeck = it.element.ancestorOrSelf<RsUseSpeck>()
                val extendedSpeck = getExtendedUseSpeck(useGroup, useSpeck)
                extendedSpeck?.deleteWithSurroundingComma()
            } else {
                it.element.ancestorOrSelf<RsUseItem>()?.delete()
            }
        }
    }

    private fun getExtendedUseSpeck(element: PsiElement, useSpeck: RsUseSpeck?): RsUseSpeck? {
        return when (element) {
            is RsUseGroup -> element.children.map { getExtendedUseSpeck(it, useSpeck) }
                .firstOrNull { it != null }
            is RsUseSpeck -> if (element == useSpeck || element.anyDescendantOfType<RsUseSpeck> { it == useSpeck }) {
                element
            } else {
                null
            }
            else -> null
        }
    }

    private fun getLowestGroupWithMultipleChildren(it: PsiReference): RsUseGroup? {
        return it.element.ancestors.filterIsInstance<RsUseGroup>().firstOrNull { it.children.size > 1 }
    }

    private fun replaceParameterUsages(functionDup: RsFunction, caller: PsiElement, funcScope: LocalSearchScope) {
        val parameterBindingsList = functionDup.valueParameters
            .filter { it !is RsSelfParameter }
            .map { it.pat }
            .filterIsInstance(RsPat::class.java)

        val valueArgumentList = when (caller) {
            is RsCallExpr -> caller.valueArgumentList
            is RsDotExpr -> caller.methodCall?.valueArgumentList ?: return
            else -> return
        }

        val arguments = if (isDoubleSemicolonMethodCall(functionDup, caller)) {
            valueArgumentList.exprList.drop(1)
        } else {
            valueArgumentList.exprList
        }

        val paramUsagesToReplace = mutableMapOf<RsExpr?, PsiElement>()

        for ((index, parameter) in parameterBindingsList.withIndex()) {
            paramUsagesToReplace.putAll(replaceParameterUsage(arguments, index, parameter, caller, funcScope))
        }
        paramUsagesToReplace.forEach { it.key?.replace(it.value) }
    }

    private fun replaceParameterUsage(arguments: List<RsExpr>, index: Int, parameter: RsPat, caller: PsiElement,
                                      funcScope: LocalSearchScope): Map<RsExpr?, @NotNull PsiElement> {
        if (arguments.size <= index) {
            return emptyMap()
        }

        val argument = arguments[index]
        var name: String = parameter.text.removePrefix("mut ")

        if (name != argument.text) {
            if (argument.childrenWithLeaves.any { it.text == name }) {
                name += "_argument"
            }
            val bindingMutability = when (parameter) {
                is RsPatIdent -> parameter.patBinding.mutability.isMut
                else -> false
            }
            val letDeclaration = factory.createLetDeclaration(name, argument, bindingMutability)
            addLetDeclarationNearCaller(caller, letDeclaration)
            return getParamUsagesToReplace(parameter, funcScope, letDeclaration)
        }

        return emptyMap()
    }

    private fun addLetDeclarationNearCaller(caller: PsiElement, letDeclaration: RsLetDecl) {
        val callerParentStmt = caller.ancestorOrSelf<RsStmt>()
        if (callerParentStmt != null) {
            callerParentStmt.addLeftSibling(letDeclaration)
        } else {
            caller.addLeftSibling(letDeclaration)
        }
    }

    private fun getParamUsagesToReplace(parameter: RsPat, funcScope: LocalSearchScope, letDeclaration: RsLetDecl):
        MutableMap<RsExpr?, PsiElement> {
        val paramUsagesToReplace = mutableMapOf<RsExpr?, PsiElement>()
        val paramNavigationElement = (parameter as? RsPatIdent)?.patBinding?.navigationElement
            ?: parameter.navigationElement
        ReferencesSearch.search(paramNavigationElement, funcScope).findAll().forEach {
            val declarationID = when (val declarationVarPat = letDeclaration.pat) {
                is RsPatIdent -> declarationVarPat.patBinding.identifier
                else -> declarationVarPat
            }
            if (declarationID != null) {
                val declarationElementExpr: RsExpr? = it.element.ancestorOrSelf()
                paramUsagesToReplace[declarationElementExpr] = declarationID
            }
        }
        return paramUsagesToReplace
    }

    private fun addFunctionBodyToCallerWithCorrectSpacing(
        leftSibling: PsiElement?,
        rightSibling: PsiElement?,
        blockParent: RsBlock,
        body: RsBlock,
        childContainingEnclosingStatement: PsiElement?
    ) {
        if (leftSibling != null) {
            val isThereSpaceBeforeFunctionCall = leftSibling.nextSibling.text.startsWith("\n\n")
            val isThereSpaceAfterFunctionCall = rightSibling?.prevSibling?.text?.startsWith("\n\n") == true
            blockParent.addRangeAfter(body.children.first(), body.children.last(), leftSibling)
            if (isThereSpaceBeforeFunctionCall || isThereSpaceAfterFunctionCall) {
                blockParent.addAfter(factory.createWhitespace("\n\n    "), leftSibling)
            }
        } else if (rightSibling != null) {
            val isThereSpaceAfterFunctionCall = rightSibling.prevSibling.text.startsWith("\n\n")
            blockParent.addRangeBefore(body.children.first(), body.children.last(), rightSibling)
            if (isThereSpaceAfterFunctionCall) {
                blockParent.addBefore(factory.createWhitespace("\n\n    "), rightSibling)
            }
        } else if (childContainingEnclosingStatement?.isPhysical == true) {
            blockParent.addBefore(factory.createWhitespace("\n    "), childContainingEnclosingStatement)
            blockParent.addRangeBefore(body.children.first(), body.children.last(), childContainingEnclosingStatement)
            blockParent.addBefore(factory.createNewline(), childContainingEnclosingStatement)
        } else {
            blockParent.addStatements(body.children)
        }
    }

    private fun replaceCallerWithRetExpr(body: RsBlock, caller: PsiElement) {
        val retExpr = body.descendantsOfType<RsRetExpr>().firstOrNull()
        if (retExpr != null) {
            val retExprInside = retExpr.expr
            if (retExprInside != null) {


                // Covering a case in which ; isn't included in the expression, and parent is too wide.
                // e.g. if RsExpr is surrounded by RsBlock going to the RsBlock won't help.
                val actualRetExpr: PsiElement = if (retExpr.parent.text == retExpr.text + ";") {
                    retExpr.parent
                } else {
                    retExpr
                }
                if (caller.parent !is RsBlock && caller.parent.text != caller.text + ";") {
                    caller.replace(retExprInside)
                    retExpr.delete()
                } else {
                    actualRetExpr.replace(retExprInside)
                    if (caller.parent.text == caller.text + ";") {
                        caller.parent.delete()
                    } else {
                        caller.delete()
                    }
                }
            }
        } else {
            caller.delete()
        }
    }

    private fun replaceSelfParamWithExpr(selfParam: RsSelfParameter?, caller: PsiElement, funcScope: LocalSearchScope) {
        if (selfParam != null) {
            val selfExprText = buildString {
                if (selfParam.mut != null) append("mut")
                append(" ")
                val callerText = when {
                    caller is RsDotExpr -> caller.expr.text
                    caller is RsCallExpr && caller.expr is RsPathExpr ->
                        (caller as RsCallExprImpl).valueArgumentList.exprList[0].text
                    else -> caller.text
                }
                append(callerText)
            }

            val selfExpr = factory.tryCreateExpression(selfExprText)!!
            ReferencesSearch.search(selfParam.navigationElement, funcScope).findAll().forEach {
                it.element.ancestorOrSelf<RsExpr>()!!.replace(selfExpr)
            }
        }
    }

    private fun replaceLastExprToStatement(body: RsBlock) {
        val expr = body.expr ?: return
        val text = buildString {
            if (expr !is RsRetExpr) append("return ")
            append(expr.text)
            append(";")
        }

        val stmt = factory.createStatement(text)
        expr.replace(stmt)
    }

    private fun PsiElement.addLeftSibling(element: PsiElement) {
        this.parent.addBefore(element, this)
    }
}
