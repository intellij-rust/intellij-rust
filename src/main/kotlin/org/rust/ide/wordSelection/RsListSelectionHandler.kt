/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*

class RsListSelectionHandler : ExtendWordSelectionHandlerBase() {
    override fun canSelect(e: PsiElement): Boolean =
        e is RsTypeArgumentList || e is RsValueArgumentList || e is RsTypeParameterList || e is RsValueParameterList

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        val node = e.node!!
        val startNode = node.findChildByType(RS_LIST_OPEN_SYMBOLS) ?: return null
        val endNode = node.findChildByType(RS_LIST_CLOSE_SYMBOLS) ?: return null
        val range = TextRange(startNode.startOffset + 1, endNode.startOffset)
        return listOf(range)
    }
}
