/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.extractFunction

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.utils.findStatementsInRange
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.isAssocFn
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.psi.ext.parentOfType

class RsExtractFunctionConfig private constructor(
    val containingFunction: RsFunction,
    val elements: List<PsiElement>,
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
            return RsExtractFunctionConfig(
                fn,
                elements,
                needsSelf = fn.owner.isImplOrTrait && !fn.isAssocFn
            )
        }
    }
}
