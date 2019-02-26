/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.hint.ImplementationTextSelectioner
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsOrPats
import org.rust.lang.core.psi.RsPat
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.startOffset

class RsImplementationTextSelectioner : ImplementationTextSelectioner {

    override fun getTextEndOffset(element: PsiElement): Int = element.definitionRoot.endOffset
    override fun getTextStartOffset(element: PsiElement): Int = element.definitionRoot.startOffset

    private val PsiElement.definitionRoot: PsiElement get() {
        var element = this
        while (element is RsPatBinding || element is RsPat || element is RsOrPats) {
            element = element.parent
        }
        return element
    }
}
