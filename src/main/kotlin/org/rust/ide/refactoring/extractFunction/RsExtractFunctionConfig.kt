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
import com.intellij.psi.util.siblings
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.refactoring.RsFunctionSignatureConfig
import org.rust.ide.utils.findElementAtIgnoreWhitespaceAfter
import org.rust.ide.utils.findStatementsOrExprInRange
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.ty.TyTuple
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type
import org.rust.stdext.buildList

class ReturnValue(val exprText: String?, val type: Ty) {
    companion object {
        fun direct(expr: RsExpr): ReturnValue =
            ReturnValue(null, expr.type)

        fun namedValue(value: RsPatBinding): ReturnValue =
            ReturnValue(value.referenceName, value.type)

        fun tupleNamedValue(value: List<RsPatBinding>): ReturnValue = ReturnValue(
            value.joinToString(", ", postfix = ")", prefix = "(") { it.referenceName },
            TyTuple(value.map { it.type })
        )
    }
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
                (isUsedAfterEnd && binding.type !is TyReference && !implLookup.isCopy(binding.type))

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
    val returnValue: ReturnValue? = null,
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

    private val returnType: Ty
        get() = returnValue?.type ?: TyUnit.INSTANCE

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
        (parameterTypes + returnType).forEach {
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
        if (returnValue != null && returnValue.type !is TyUnit) {
            append(" -> ${returnValue.type.renderInsertionSafe()}")
        }
        append(whereClausesText)
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
                val returnExprText = returnValue?.exprText.orEmpty()

                val bodyContent = buildList {
                    addAll(unselectedParamsTexts)
                    add(elementsText)
                    if (returnExprText.isNotEmpty()) {
                        add(returnExprText)
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

            val returnValue = when (innerBindings.size) {
                0 -> when {
                    last is RsExpr -> ReturnValue.direct(last)
                    last is RsExprStmt && last.isTailStmt -> ReturnValue.direct(last.expr)
                    else -> null
                }
                1 -> ReturnValue.namedValue(innerBindings[0])
                else -> ReturnValue.tupleNamedValue(innerBindings)
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

            return RsExtractFunctionConfig(
                fn,
                elements,
                returnValue = returnValue,
                parameters = parameters,
                isAsync = isAsync,
                isUnsafe = fn.isUnsafe
            )
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
