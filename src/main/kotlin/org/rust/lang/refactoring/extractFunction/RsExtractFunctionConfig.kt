/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.extractFunction

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.rust.ide.utils.findStatementsInRange
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.isAssocFn
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.lang.core.types.type

class RsExtractFunctionConfig private constructor(
    val containingFunction: RsFunction,
    val elements: List<PsiElement>,
    val returnType: String? = null,
    val returnBindingName: String? = null,
    var name: String = "",
    var visibilityLevelPublic: Boolean = false,
    val needsSelf: Boolean
) {

    companion object {
        fun create(file: PsiFile, start: Int, end: Int): RsExtractFunctionConfig? {
            val elements = findStatementsInRange(file, start, end).asList()
            if (elements.isEmpty()) return null
            val first = elements.first()
            val last = elements.last()

            // check element should be a part of one block
            val fn = first.parentOfType<RsFunction>() ?: return null
            if (fn != last.parentOfType<RsFunction>()) return null

            val letBindings = fn.descendantsOfType<RsPatBinding>().filter { it.textOffset <= end }

            val innerBinding = letBindings.filter { it.textOffset >= start }
                .filter {
                    ReferencesSearch.search(it, LocalSearchScope(fn))
                        .asSequence()
                        .any { ref -> ref.element.textOffset > end }
                }
            val (returnBindingName, returnType) = if (innerBinding.isEmpty()) {
                null to null
            } else if (innerBinding.size == 1) {
                innerBinding.map { it.referenceName }.joinToString(", ") to innerBinding.map { it.type }.joinToString(", ")
            } else {
                innerBinding.map { it.referenceName }.joinToString(", ", postfix = ")", prefix = "(") to innerBinding.map { it.type }.joinToString(", ", postfix = ")", prefix = "(")
            }

            return RsExtractFunctionConfig(
                fn,
                elements,
                returnType = returnType,
                returnBindingName = returnBindingName,
                needsSelf = fn.owner.isImplOrTrait && !fn.isAssocFn
            )
        }
    }
}

