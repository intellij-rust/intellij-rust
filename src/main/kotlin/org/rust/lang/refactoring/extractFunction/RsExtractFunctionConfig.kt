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
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.isAssocFn
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.psi.ext.ancestorStrict
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

class RsExtractFunctionConfig private constructor(
    val containingFunction: RsFunction,
    val elements: List<PsiElement>,
    val returnValue: ReturnValue? = null,
    var name: String = "",
    var visibilityLevelPublic: Boolean = false,
    val needsSelf: Boolean
) {
    val signature: String
        get() {
            var signature = "fn $name(${if (needsSelf) "self" else ""})"
            if (returnValue != null) {
                signature += " -> ${returnValue.type.insertionSafeText}"
            }
            if (visibilityLevelPublic) {
                signature = "pub " + signature
            }
            val stmts = elements.map { it.text }.toMutableList()
            if (returnValue?.expression != null) {
                stmts.add(returnValue.expression)
            }
            return signature + "{\n${stmts.joinToString(separator = "\n")}\n}"
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

            val letBindings = fn.descendantsOfType<RsPatBinding>().filter { it.textOffset <= end }

            val innerBinding = letBindings.filter { it.textOffset >= start }
                .filter {
                    ReferencesSearch.search(it, LocalSearchScope(fn))
                        .asSequence()
                        .any { ref -> ref.element.textOffset > end }
                }

            val returnValue = when (innerBinding.size) {
                0 -> if (last is RsExpr) ReturnValue.direct(last) else null
                1 -> ReturnValue.namedValue(innerBinding[0])
                else -> ReturnValue.tupleNamedValue(innerBinding)
            }

            return RsExtractFunctionConfig(
                fn,
                elements,
                returnValue = returnValue,
                needsSelf = fn.owner.isImplOrTrait && !fn.isAssocFn
            )
        }
    }
}

