/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import com.intellij.codeInsight.editorActions.moveUpDown.LineMover
import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.elementType
import org.rust.openapiext.Testmark


abstract class RsLineMover : LineMover() {
    final override fun checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean {
        if (file !is RsFile && super.checkAvailable(editor, file, info, down)) return false
        @Suppress("USELESS_ELVIS") // NotNull annotation is wrong :(
        val originalRange = info.toMove ?: return false
        val psiRange = StatementUpDownMover.getElementRange(editor, file, originalRange) ?: return false
        if (psiRange.first == null || psiRange.second == null) return false

        val firstItem = findMovableAncestor(psiRange.first) ?: return false
        val lastItem = findMovableAncestor(psiRange.second) ?: return false
        val sibling = StatementUpDownMover.firstNonWhiteElement(
            if (down) lastItem.nextSibling else firstItem.prevSibling,
            down
        )
        // Either reached last sibling, or jumped over multi-line whitespace
        if (sibling == null) {
            info.toMove2 = null
            return true
        }
        info.toMove = LineRange(firstItem, lastItem)
        info.toMove2 = findTargetLineRange(sibling, down)
        return true
    }

    abstract protected fun findMovableAncestor(psi: PsiElement): PsiElement?
    abstract protected fun findTargetLineRange(sibling: PsiElement, down: Boolean): LineRange?

    companion object {
        fun isMovingOutOfBlock(sibling: PsiElement, down: Boolean): Boolean =
            sibling.elementType == (if (down) RsElementTypes.RBRACE else RsElementTypes.LBRACE)
    }
}

object UpDownMoverTestMarks {
    val moveOutOfImpl = Testmark("moveOutOfImpl")
    val moveOutOfMatch = Testmark("moveOutOfMatch")
}
