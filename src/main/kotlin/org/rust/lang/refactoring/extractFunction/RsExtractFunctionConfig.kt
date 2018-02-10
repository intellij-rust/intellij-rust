/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.extractFunction

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.rust.ide.presentation.insertionSafeText
import org.rust.ide.utils.findStatementsInRange
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Ty
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

class Parameter(val name: String, val type: Ty? = null) {
    companion object {
        fun direct(value: RsPatBinding): Parameter {
            return Parameter(value.referenceName, value.type)
        }

        fun self(value: String): Parameter {
            return Parameter(value)
        }
    }

    val text: String
        get() = if (type != null) {
            "$name: ${type.insertionSafeText}"
        } else {
            name
        }
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
        get() = parameters.joinToString(",") { it.text }

    val argumentsText: String
        get() = parameters.filter { it.type != null }.joinToString(",") { it.name }

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
            val stmts = elements.map { it.text }.toMutableList()
            if (returnValue?.expression != null) {
                stmts.add(returnValue.expression)
            }
            append("{\n${stmts.joinToString(separator = "\n")}\n}")
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
            val filtered = wherePredList.filter { typeParams.contains(it.typeReference?.type) }
            if (filtered.isEmpty()) return ""
            return filtered.joinToString(separator = ",", prefix = " where ") { it.text }
        }

    private fun typeParameters(): List<RsTypeParameter> {
        val bounds = typeParameterBounds()
        val paramAndReturnTypes = (parameters.mapNotNull { it.type } + listOf(returnValue).mapNotNull { it?.type })
        return containingFunction.typeParameters.filter {
            val typeParameter = it.declaredType
            paramAndReturnTypes.any {
                it.types().contains(typeParameter) || it.dependTypes(bounds).contains(typeParameter)
            }
        }
    }

    private fun typeParameterBounds(): Map<Ty, List<Ty>> {
        return containingFunction.typeParameters.associate {
            val type = it.declaredType
            val bounds = it.bounds.flatMap {
                it.bound.traitRef?.path?.typeArgumentList?.typeReferenceList?.flatMap { it.type.types() } ?: emptyList()
            }
            type to bounds
        }
    }

    companion object {
        fun create(file: PsiFile, start: Int, end: Int): RsExtractFunctionConfig? {
            val elements = findStatementsInRange(file, start, end).asList()
            if (elements.isEmpty()) return null
            val first = elements.first()
            val last = elements.last()

            // check element should be a part of one block
            val fn = first.ancestorStrict<RsFunction>() ?: return null
            if (fn != last.ancestorStrict<RsFunction>()) return null

            val letBindings = fn.descendantsOfType<RsPatBinding>()
                .filter { it.textOffset <= end }

            val parameters = letBindings
                .filter { it.textOffset <= start }
                .filter {
                    ReferencesSearch.search(it, LocalSearchScope(fn))
                        .any { ref -> ref.element.textOffset in start..end }
                }
                .map { Parameter.direct(it) }
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

private fun Ty.types(): List<Ty> {
    val types = mutableListOf<Ty>()

    fun collect(type: Ty) {
        types.add(type)
        type.typeParameterValues.values.forEach { collect(it) }
    }

    collect(this)

    return types
}

private fun Ty.dependTypes(boundMap: Map<Ty, List<Ty>>): List<Ty> {
    val types = mutableListOf<Ty>()

    fun collect(type: Ty) {
        val bounds = boundMap[type]?.filter { !types.contains(it) } ?: return
        types.addAll(bounds)
        bounds.forEach { collect(it) }
    }

    collect(this)

    return types
}
