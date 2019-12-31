/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsBlockSelectionHandler : ExtendWordSelectionHandlerBase() {
    override fun canSelect(e: PsiElement): Boolean =
        e is RsBlock || e is RsBlockFields || e is RsStructLiteralBody ||
            e is RsEnumBody || e is RsMembers || e is RsMatchBody


    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        val startNode =
            e.childrenWithLeaves.firstOrNull { it.elementType == RsElementTypes.LBRACE }
                ?.rightSiblings?.firstOrNull { it !is PsiWhiteSpace }
                ?: return null

        val endNode =
            startNode.rightSiblings.firstOrNull { it.elementType == RsElementTypes.RBRACE }
                ?.leftSiblings?.firstOrNull { it !is PsiWhiteSpace }
                ?: return null

        val startOffset = startNode.startOffset
        val endOffset = endNode.endOffset
        if (startOffset >= endOffset) return null

        val range = TextRange.create(startOffset, endOffset)
        return expandToWholeLine(editorText, range)
    }
}
