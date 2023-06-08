/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractFunction

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.descendants
import com.intellij.psi.util.isAncestor
import com.intellij.psi.util.siblings
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.refactoring.RsFunctionSignatureConfig
import org.rust.ide.refactoring.extractFunction.ControlFlow.TryOperatorInfo
import org.rust.ide.utils.findElementAtIgnoreWhitespaceAfter
import org.rust.ide.utils.findStatementsOrExprInRange
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.stdext.buildList
import org.rust.stdext.mapToSet

/**
 * `let (a, b) = extracted();`
 *      ~~~~~~ [exprText]
 * [type] = TyTuple(i32, i32)
 */
class OutputVariables(val exprText: String?, val type: Ty) {
    companion object {
        fun direct(expr: RsExpr): OutputVariables =
            OutputVariables(null, expr.type.unitIfNever())

        fun namedValue(value: RsPatBinding): OutputVariables =
            OutputVariables(value.referenceName, value.type.unitIfNever())

        fun tupleNamedValue(value: List<RsPatBinding>): OutputVariables = OutputVariables(
            value.joinToString(", ", postfix = ")", prefix = "(") { it.referenceName },
            TyTuple(value.map { it.type })
        )
    }
}

/**
 * | Extracted control-flow       | Extracted type | Resulted return type       | Resulted invocation                                   |
 * |------------------------------|----------------|----------------------------|-------------------------------------------------------|
 * | None                         | stmt           | ()                         | f();                                                  |
 * | None                         | expr           | T                          | let x = f();                                          |
 * | only return/break/continue   | stmt           | bool                       | if f() return;                                        |
 * | only return/break/continue   | expr           | Option<T>                  | let x = match f() { Some(v) => v, None => return };   |
 * | only return/break with value | stmt           | Option<E>                  | if let Some(v) = f() { return v; }                    |
 * | only return/break with value | expr           | Result<T, E>               | let x = match f() { Ok(v) => v, Err(v) => return v }; |
 * | only ? operator              | stmt           | Result<(), E> / Option<()> | f()?;                                                 |
 * | only ? operator              | expr           | Result<T, E> / Option<T>   | let x = f()?;                                         |
 */
enum class ReturnKind {
    VALUE,                // () or T
    BOOL,                 // bool
    OPTION_CONTROL_FLOW,  // Option<E>
    OPTION_VALUE,         // Option<T>
    RESULT,               // Result<T, E>
    TRY_OPERATOR,         // Result or Option
}

class ControlFlow(
    /** "return", "break", "break 'label", "continue" or "continue 'label" */
    val text: String,
    /**
     * `return;` - TyUnit
     * `return 1;` - i32
     */
    val type: Ty,
    val tryOperatorInfo: TryOperatorInfo?,
) {
    class TryOperatorInfo(val successVariant: String, val generateType: (String) -> String)
}

class Parameter private constructor(
    var name: String,
    val type: Ty? = null,
    private val isReference: Boolean = false,
    var isMutable: Boolean = false,
    private val requiresMut: Boolean = false,
    var isSelected: Boolean = true
) {
    /** Original name of the parameter (parameter renaming does not affect it) */
    private val originalName = name

    private val mutText: String
        get() = if (isMutable && (!isReference || requiresMut)) "mut " else ""
    private val referenceText: String
        get() = if (isReference) {
            if (isMutable) "&mut " else "&"
        } else {
            ""
        }
    private val typeText: String = type?.renderInsertionSafe().orEmpty()

    val originalParameterText: String
        get() = if (type != null) "$mutText$originalName: $referenceText$typeText" else originalName

    val parameterText: String
        get() = if (type != null) "$mutText$name: $referenceText$typeText" else name

    val argumentText: String
        get() = "$referenceText$originalName"

    val isSelf: Boolean
        get() = type == null

    companion object {
        private fun direct(value: RsPatBinding, requiredBorrowing: Boolean, requiredMutableValue: Boolean): Parameter {
            val reference = when {
                requiredMutableValue -> requiredBorrowing
                value.mutability.isMut -> true
                requiredBorrowing -> true
                else -> false
            }
            val mutable = when {
                requiredMutableValue -> true
                value.mutability.isMut -> true
                else -> false
            }
            return Parameter(value.referenceName, value.type, reference, mutable, requiredMutableValue)
        }

        fun self(name: String): Parameter =
            Parameter(name)

        // TODO: Get rid of the heuristics and implement proper borrow analysis
        fun build(
            binding: RsPatBinding,
            references: List<PsiReference>,
            isUsedAfterEnd: Boolean,
            implLookup: ImplLookup
        ): Parameter {
            val hasRefOperator = references.any {
                val operatorType = (it.element.ancestorStrict<RsUnaryExpr>())?.operatorType
                operatorType == UnaryOperator.REF || operatorType == UnaryOperator.REF_MUT
            }
            val requiredBorrowing = hasRefOperator ||
                (isUsedAfterEnd && binding.type !is TyReference && !implLookup.isCopy(binding.type).isTrue)

            val requiredMutableValue = binding.mutability.isMut && references.any {
                if (it.element.ancestorStrict<RsValueArgumentList>() == null) return@any false
                val operatorType = it.element.ancestorStrict<RsUnaryExpr>()?.operatorType
                operatorType == null || operatorType == UnaryOperator.REF_MUT
            }

            return direct(binding, requiredBorrowing, requiredMutableValue)
        }
    }
}

class RsExtractFunctionConfig private constructor(
    function: RsFunction,
    val elements: List<PsiElement>,
    val outputVariables: OutputVariables,
    val controlFlow: ControlFlow?,
    val returnKind: ReturnKind,
    var name: String = "",
    var visibilityLevelPublic: Boolean = false,
    val isAsync: Boolean = false,
    val isUnsafe: Boolean = false,
    var parameters: List<Parameter>
) : RsFunctionSignatureConfig(function) {
    val valueParameters: List<Parameter>
        get() = parameters.filter { !it.isSelf }

    private val parametersText: String
        get() = parameters.filter { it.isSelected }.joinToString(", ") { it.parameterText }

    private val originalParametersText: String
        get() = parameters.filter { it.isSelected }.joinToString(", ") { it.originalParameterText }

    val argumentsText: String
        get() = valueParameters.filter { it.isSelected }.joinToString(", ") { it.argumentText }

    val signature: String
        get() = signature(false)

    private val parameterTypes: List<Ty>
        get() = parameters.mapNotNull { it.type }

    val parametersAndReturnTypes: List<Ty>
        get() = parameterTypes + listOfNotNull(outputVariables.type, controlFlow?.type)

    private fun typeParameterBounds(): Map<Ty, Set<Ty>> =
        function.typeParameters.associate { typeParameter ->
            val type = typeParameter.declaredType
            val bounds = mutableSetOf<Ty>()
            typeParameter.bounds.flatMapTo(bounds) { polybound ->
                polybound.bound.traitRef?.path?.typeArguments?.flatMap { it.rawType.types() }.orEmpty()
            }
            type to bounds
        }

    override fun typeParameters(): List<RsTypeParameter> {
        val bounds = typeParameterBounds()
        val paramAndReturnTypes = mutableSetOf<Ty>()
        parametersAndReturnTypes.forEach {
            paramAndReturnTypes.addAll(it.types())
            paramAndReturnTypes.addAll(it.dependTypes(bounds))
        }
        return function.typeParameters.filter { it.declaredType in paramAndReturnTypes }
    }

    /**
     * - Original signature is used when the extracted function is inserting to the source code
     * - Real signature is used when the signature is rendering inside [DialogExtractFunctionUi]
     */
    private fun signature(isOriginal: Boolean): String = buildString {
        if (visibilityLevelPublic) {
            append("pub ")
        }
        if (isAsync) {
            append("async ")
        }
        if (isUnsafe) {
            append("unsafe ")
        }
        append("fn $name$typeParametersText(${if (isOriginal) originalParametersText else parametersText})")
        renderReturnType()?.let { append(" -> $it") }
        append(whereClausesText)
    }

    private fun renderReturnType(): String? {
        val outputVariablesType = outputVariables.type.renderInsertionSafe()
        val controlFlowType = controlFlow?.type?.renderInsertionSafe()
        return when (returnKind) {
            ReturnKind.VALUE -> outputVariablesType.takeIf { it != "()" }
            ReturnKind.BOOL -> "bool"
            ReturnKind.OPTION_CONTROL_FLOW -> "Option<$controlFlowType>"
            ReturnKind.OPTION_VALUE -> "Option<$outputVariablesType>"
            ReturnKind.RESULT -> "Result<$outputVariablesType, $controlFlowType>"
            ReturnKind.TRY_OPERATOR -> controlFlow!!.tryOperatorInfo!!.generateType(outputVariablesType)
        }
    }

    val functionText: String
        get() = buildString {
            append(signature(true))
            val single = elements.singleOrNull()
            val body = if (single is RsBlockExpr) {
                single.block.text
            } else {
                val unselectedParamsTexts = parameters
                    .filter { !it.isSelected }
                    .map { "let ${it.name}: ${it.type} ;\n" }
                val elementsText = if (elements.isNotEmpty()) {
                    val last = elements.last()
                    val allElements = elements.first().siblings().takeWhile { it != last } + last
                    allElements.joinToString(separator = "") { it.text }
                } else {
                    ""
                }
                val outputVariablesText = outputVariables.exprText.orEmpty()

                val bodyContent = buildList {
                    addAll(unselectedParamsTexts)
                    add(elementsText)
                    if (outputVariablesText.isNotEmpty()) {
                        add(outputVariablesText)
                    }
                }

                bodyContent.joinToString(separator = "\n", prefix = "{\n", postfix = "\n}")
            }
            append(body)
        }

    companion object {
        fun create(file: PsiFile, start: Int, end: Int): RsExtractFunctionConfig? {
            doCreate(file, start, end)?.let { return it }

            file.findElementAtIgnoreWhitespaceAfter(end - 1)?.let { lastElement ->
                if (lastElement.elementType in listOf(RsElementTypes.COMMA, RsElementTypes.SEMICOLON)) {
                    return doCreate(file, start, lastElement.startOffset)
                }
            }
            return null
        }

        private fun doCreate(file: PsiFile, start: Int, end: Int): RsExtractFunctionConfig? {
            val elements = findStatementsOrExprInRange(file, start, end).asList()
            if (elements.isEmpty()) return null
            val first = elements.first()
            val last = elements.last()

            // check elements should be a part of one block
            val fn = first
                .ancestorStrict<RsFunction>()
                .takeIf { it == last.ancestorStrict<RsFunction>() }
                ?: return null

            val letBindings = fn.descendantsOfType<RsPatBinding>()
                .filter { it.textOffset <= end }

            val implLookup = ImplLookup.relativeTo(fn)
            val parameters = letBindings.mapNotNull { binding ->
                if (binding.textOffset > start) return@mapNotNull null
                val result = ReferencesSearch.search(binding, LocalSearchScope(fn))
                val targets = result.filter { it.element.textOffset in start..end }
                if (targets.isEmpty()) return@mapNotNull null
                val isUsedAfterEnd = result.any { it.element.textOffset > end }

                Parameter.build(binding, targets, isUsedAfterEnd, implLookup)
            }.toMutableList()

            val innerBindings = letBindings
                .filter { it.textOffset >= start }
                .filter {
                    ReferencesSearch
                        .search(it, LocalSearchScope(fn))
                        .any { ref -> ref.element.textOffset > end }
                }

            val outputVariables = when (innerBindings.size) {
                0 -> when {
                    last is RsExpr -> OutputVariables.direct(last)
                    last is RsExprStmt && last.isTailStmt -> OutputVariables.direct(last.expr)
                    else -> OutputVariables(exprText = null, type = TyUnit.INSTANCE)
                }

                1 -> OutputVariables.namedValue(innerBindings[0])
                else -> OutputVariables.tupleNamedValue(innerBindings)
            }
            val selfParameter = fn.selfParameter
            if (fn.owner.isImplOrTrait && selfParameter != null) {
                val used = ReferencesSearch
                    .search(selfParameter, LocalSearchScope(fn))
                    .any { ref -> ref.element.textOffset in start..end }
                if (used) {
                    parameters.add(0, Parameter.self(selfParameter.text))
                }
            }

            var isAsync = false
            if (fn.isAsync) {
                val visitor = object : RsRecursiveVisitor() {
                    override fun visitFieldLookup(o: RsFieldLookup) {
                        if (o.isAsync) {
                            isAsync = true
                        }
                    }

                    // stop recursive propagation, we want to ignore awaits in async blocks and async closures
                    override fun visitBlockExpr(o: RsBlockExpr) {
                        if (!o.isAsync) {
                            super.visitBlockExpr(o)
                        }
                    }

                    override fun visitLambdaExpr(o: RsLambdaExpr) {
                        if (!o.isAsync) {
                            super.visitLambdaExpr(o)
                        }
                    }
                }
                for (element in elements) {
                    element.accept(visitor)
                }
            }

            val controlFlow = extractControlFlow(elements)
            return RsExtractFunctionConfig(
                fn,
                elements,
                outputVariables = outputVariables,
                controlFlow = controlFlow,
                returnKind = determineReturnKind(outputVariables, controlFlow),
                parameters = parameters,
                isAsync = isAsync,
                isUnsafe = fn.isUnsafe
            )
        }

        private fun extractControlFlow(extractedElements: List<PsiElement>): ControlFlow? {
            val (controlFlowOwner, controlFlowElements) = findControlFlowElements(extractedElements) ?: return null
            // Mixing return/break/continue is not supported
            if (controlFlowElements.mapToSet { it.elementType }.size != 1) return null
            val controlFlowText = when (val element = controlFlowElements.first()) {
                is RsLabelReferenceOwner -> {
                    val label = element.label?.let { " ${it.text}" } ?: ""
                    "${element.operator.text}$label"
                }
                is RsRetExpr -> "return"
                is RsTryExpr -> "?"
                else -> error("unreachable")
            }

            val type = when (controlFlowOwner) {
                is RsFunction -> controlFlowOwner.rawReturnType
                is RsLambdaExpr -> controlFlowOwner.returnType ?: return null
                else -> (controlFlowOwner as? RsExpr)?.type ?: return null
            }.unitIfNever()

            val tryOperatorInfo = if (controlFlowText == "?") {
                createTryOperatorInfo(controlFlowOwner, type) ?: return null
            } else {
                null
            }

            return ControlFlow(controlFlowText, type, tryOperatorInfo)
        }

        private fun createTryOperatorInfo(context: PsiElement, type: Ty): TryOperatorInfo? {
            val knownItems = context.ancestorOrSelf<RsElement>()?.knownItems ?: return null
            if (type !is TyAdt) return null
            return when (type.item) {
                knownItems.Option -> TryOperatorInfo("Some") { "Option<$it>" }
                knownItems.Result -> {
                    val errorType = type.typeArguments.getOrNull(1)?.renderInsertionSafe() ?: "_"
                    TryOperatorInfo("Ok") { "Result<$it, $errorType>" }
                }
                else -> null
            }
        }

        private fun determineReturnKind(returnValue: OutputVariables?, controlFlow: ControlFlow?): ReturnKind {
            if (controlFlow?.text == "?") return ReturnKind.TRY_OPERATOR

            val returnValueType = returnValue?.type?.takeIf { it !is TyUnit }
            val controlFlowType = controlFlow?.type?.takeIf { it !is TyUnit }
            return when {
                controlFlow == null -> ReturnKind.VALUE
                returnValueType == null -> if (controlFlowType == null) ReturnKind.BOOL else ReturnKind.OPTION_CONTROL_FLOW
                else -> if (controlFlowType == null) ReturnKind.OPTION_VALUE else ReturnKind.RESULT
            }
        }
    }
}

fun Ty.types(): Set<Ty> {
    val types = mutableSetOf<Ty>()

    fun collect(type: Ty) {
        types.add(type)
        type.typeParameterValues.types.forEach { collect(it) }
    }

    collect(this)

    return types
}

fun Ty.dependTypes(boundMap: Map<Ty, Set<Ty>>): Set<Ty> {
    val types = mutableSetOf<Ty>()

    fun collect(type: Ty) {
        val bounds = boundMap[type]?.filter { it !in types } ?: return
        types.addAll(bounds)
        bounds.forEach { collect(it) }
    }

    collect(this)

    return types
}

private fun Ty.unitIfNever(): Ty = if (this is TyNever) TyUnit.INSTANCE else this

data class ControlFlowElements(val controlFlowOwner: PsiElement?, val controlFlowElements: List<PsiElement>)

fun findControlFlowElements(extractedElements: List<PsiElement>): ControlFlowElements? {
    val (controlFlowOwners, controlFlowElements) = extractedElements
        .flatMap { extractedElement ->
            extractedElement.controlFlowElements().mapNotNull {
                val owner = it.findControlFlowOwner()
                if (owner != null && extractedElement.isAncestor(owner)) return@mapNotNull null
                owner to it
            }
        }
        .unzip()

    val controlFlowOwner = controlFlowOwners
        .toHashSet().also { if (it.size != 1) return null }
        .single()
    return ControlFlowElements(controlFlowOwner, controlFlowElements)
}

private fun PsiElement.controlFlowElements(): Sequence<PsiElement> =
    descendants { it !is RsFunctionOrLambda }
        .filter { it is RsRetExpr || it is RsBreakExpr || it is RsContExpr || it is RsTryExpr }

/**
 * `fn func() { return; }` - [RsFunction]
 * `|| { return; }` - [RsLambdaExpr]
 * `loop { break; }` - [RsLooplikeExpr]
 * `'label: { break 'label; }` - [RsBlockExpr]
 */
private fun PsiElement.findControlFlowOwner(): PsiElement? =
    when (this) {
        is RsRetExpr, is RsTryExpr -> ancestorStrict<RsFunctionOrLambda>()
        is RsLabelReferenceOwner -> {
            val label = label
            if (label == null) {
                ancestorStrict<RsLooplikeExpr>()
            } else {
                label.reference.resolve()?.parent
            }
        }
        else -> null
    }
