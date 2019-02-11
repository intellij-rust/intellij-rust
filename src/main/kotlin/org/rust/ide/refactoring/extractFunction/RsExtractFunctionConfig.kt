/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractFunction

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.rust.ide.presentation.insertionSafeText
import org.rust.ide.utils.findStatementsOrExprInRange
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.ty.TyTuple
import org.rust.lang.core.types.type

class ReturnValue(val expression: String?, val type: Ty) {
    companion object {
        fun direct(expr: RsExpr): ReturnValue {
            return ReturnValue(null, expr.type)
        }

        fun namedValue(value: RsPatBinding): ReturnValue {
            return ReturnValue(value.referenceName, value.type)
        }

        fun tupleNamedValue(value: List<RsPatBinding>): ReturnValue {
            return ReturnValue(
                value.joinToString(", ", postfix = ")", prefix = "(") { it.referenceName },
                TyTuple(value.map { it.type })
            )
        }
    }
}

class Parameter(
    val name: String,
    val type: Ty? = null,
    val reference: Reference = Reference.NONE,
    isMutableValue: Boolean = false
) {

    enum class Reference(val text: String) {
        MUTABLE("&mut "), IMMUTABLE("& "), NONE("")
    }

    companion object {
        fun direct(value: RsPatBinding, requiredBorrowing: Boolean, requiredMutableValue: Boolean): Parameter {
            val reference = when {
                requiredMutableValue -> if (requiredBorrowing) Reference.MUTABLE else Reference.NONE
                value.mutability.isMut -> Reference.MUTABLE
                requiredBorrowing -> Reference.IMMUTABLE
                else -> Reference.NONE
            }
            return Parameter(value.referenceName, value.type, reference, requiredMutableValue)
        }

        fun self(value: String): Parameter {
            return Parameter(value)
        }
    }

    private val mut = if (isMutableValue) "mut " else ""

    val parameterText: String
        get() = if (type != null) "$mut$name: ${reference.text}${type.insertionSafeText}" else name

    val argumentText: String
        get() = "${reference.text}$name"
}

class RsExtractFunctionConfig private constructor(
    val containingFunction: RsFunction,
    val elements: List<PsiElement>,
    val returnValue: ReturnValue? = null,
    var name: String = "",
    var visibilityLevelPublic: Boolean = false,
    val parameters: List<Parameter>
) {

    private val parametersText: String
        get() = parameters.joinToString(",") { it.parameterText }

    val argumentsText: String
        get() = parameters.filter { it.type != null }.joinToString(",") { it.argumentText }

    val signature: String
        get() = buildString {
            if (visibilityLevelPublic) {
                append("pub ")
            }
            append("fn $name$typeParametersText($parametersText)")
            if (returnValue != null) {
                append(" -> ${returnValue.type.insertionSafeText}")
            }
            append(whereClausesText)
        }

    val functionText: String
        get() = buildString {
            append(signature)
            val single = elements.singleOrNull()
            val body = if (single is RsBlockExpr) {
                single.block.text
            } else {
                val stmts = elements.map { it.text }.toMutableList()
                if (returnValue?.expression != null) {
                    stmts.add(returnValue.expression)
                }
                "{\n${stmts.joinToString(separator = "\n")}\n}"
            }
            append(body)
        }

    private val typeParametersText: String
        get() {
            val typeParams = typeParameters()
            if (typeParams.isEmpty()) return ""
            return typeParams.joinToString(separator = ",", prefix = "<", postfix = ">") { it.text }
        }

    private val whereClausesText: String
        get() {
            val wherePredList = containingFunction.whereClause?.wherePredList ?: return ""
            if (wherePredList.isEmpty()) return ""
            val typeParams = typeParameters().map { it.declaredType }
            if (typeParams.isEmpty()) return ""
            val filtered = wherePredList.filter { it.typeReference?.type in typeParams }
            if (filtered.isEmpty()) return ""
            return filtered.joinToString(separator = ",", prefix = " where ") { it.text }
        }

    private fun typeParameters(): List<RsTypeParameter> {
        val bounds = typeParameterBounds()
        val paramAndReturnTypes = mutableSetOf<Ty>()
        (parameters.mapNotNull { it.type } + listOfNotNull(returnValue?.type)).forEach {
            paramAndReturnTypes.addAll(it.types())
            paramAndReturnTypes.addAll(it.dependTypes(bounds))
        }
        return containingFunction.typeParameters.filter { it.declaredType in paramAndReturnTypes }
    }

    private fun typeParameterBounds(): Map<Ty, Set<Ty>> {
        return containingFunction.typeParameters.associate {
            val type = it.declaredType
            val bounds = mutableSetOf<Ty>()
            it.bounds.flatMapTo(bounds) {
                it.bound.traitRef?.path?.typeArgumentList?.typeReferenceList?.flatMap { it.type.types() } ?: emptyList()
            }
            type to bounds
        }
    }

    companion object {
        fun create(file: PsiFile, start: Int, end: Int): RsExtractFunctionConfig? {
            val elements = findStatementsOrExprInRange(file, start, end).asList()
            if (elements.isEmpty()) return null
            val first = elements.first()
            val last = elements.last()

            // check element should be a part of one block
            val fn = first.ancestorStrict<RsFunction>() ?: return null
            if (fn != last.ancestorStrict<RsFunction>()) return null

            val letBindings = fn.descendantsOfType<RsPatBinding>()
                .filter { it.textOffset <= end }

            val implLookup = ImplLookup.relativeTo(fn)
            val parameters = letBindings
                .mapNotNull {
                    if (it.textOffset > start) return@mapNotNull null
                    val result = ReferencesSearch.search(it, LocalSearchScope(fn))

                    val targets = result.filter { it.element.textOffset in start..end }
                    if (targets.isEmpty()) return@mapNotNull null

                    val hasRefOperator = targets.any {
                        val operatorType = (it.element.ancestorStrict<RsUnaryExpr>())?.operatorType
                        operatorType == UnaryOperator.REF || operatorType == UnaryOperator.REF_MUT
                    }
                    val requiredBorrowing = hasRefOperator || result.any { it.element.textOffset > end }
                        && it.type !is TyReference && !implLookup.isCopy(it.type)

                    val requiredMutableValue = it.mutability.isMut && targets.any {
                        if (it.element.ancestorStrict<RsValueArgumentList>() == null) return@any false
                        val operatorType = it.element.ancestorStrict<RsUnaryExpr>()?.operatorType
                        operatorType == null || operatorType == UnaryOperator.REF_MUT
                    }

                    Parameter.direct(it, requiredBorrowing, requiredMutableValue)
                }
                .toMutableList()

            val innerBindings = letBindings
                .filter { it.textOffset >= start }
                .filter {
                    ReferencesSearch.search(it, LocalSearchScope(fn))
                        .any { ref -> ref.element.textOffset > end }
                }

            val returnValue = when (innerBindings.size) {
                0 -> if (last is RsExpr) ReturnValue.direct(last) else null
                1 -> ReturnValue.namedValue(innerBindings[0])
                else -> ReturnValue.tupleNamedValue(innerBindings)
            }
            val selfParameter = fn.selfParameter
            if (fn.owner.isImplOrTrait && selfParameter != null) {
                val used = ReferencesSearch.search(selfParameter, LocalSearchScope(fn))
                    .any { ref -> ref.element.textOffset in start..end }
                if (used) parameters.add(0, Parameter.self(selfParameter.text))
            }

            return RsExtractFunctionConfig(
                fn,
                elements,
                returnValue = returnValue,
                parameters = parameters
            )
        }
    }
}

private fun Ty.types(): Set<Ty> {
    val types = mutableSetOf<Ty>()

    fun collect(type: Ty) {
        types.add(type)
        type.typeParameterValues.types.forEach { collect(it) }
    }

    collect(this)

    return types
}

private fun Ty.dependTypes(boundMap: Map<Ty, Set<Ty>>): Set<Ty> {
    val types = mutableSetOf<Ty>()

    fun collect(type: Ty) {
        val bounds = boundMap[type]?.filter { it !in types } ?: return
        types.addAll(bounds)
        bounds.forEach { collect(it) }
    }

    collect(this)

    return types
}
