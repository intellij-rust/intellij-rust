/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.topLevelPattern

class RsCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        // Don't show completion popup when typing a `let binding` identifier starting with a lowercase letter.
        // If the identifier is uppercase, the user probably wants to type a destructuring pattern
        // (`let Foo { ... }`), so we show the completion popup in this case
        if (contextElement.elementType == IDENTIFIER) {
            val identText = contextElement.node.chars
            if (identText.firstOrNull()?.isLowerCase() == true && !"mu".startsWith(identText)) {
                val parent = contextElement.parent
                if (parent is RsPatBinding && parent.topLevelPattern.parent is RsLetDecl) {
                    return ThreeState.YES
                }
            }
        }
        return ThreeState.UNSURE
    }
}
