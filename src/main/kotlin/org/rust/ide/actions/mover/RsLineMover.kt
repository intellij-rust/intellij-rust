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
    override fun checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean {
        if (file !is RsFile && super.checkAvailable(editor, file, info, down)) return false
        @Suppress("USELESS_ELVIS") // NotNull annotation is wrong :(
        val originalRange = info.toMove ?: return false
        val psiRange = StatementUpDownMover.getElementRange(editor, file, originalRange) ?: return false
        if (psiRange.first == null || psiRange.second == null) return false

        val firstItem = findMovableAncestor(psiRange.first, RangeEndpoint.START) ?: return false
        val lastItem = findMovableAncestor(psiRange.second, RangeEndpoint.END) ?: return false
        var sibling = StatementUpDownMover.firstNonWhiteElement(
            if (down) lastItem.nextSibling else firstItem.prevSibling,
            down
        )
        if (sibling != null) sibling = fixupSibling(sibling, down)
        // Either reached last sibling, or jumped over multi-line whitespace
        if (sibling == null) {
            info.toMove2 = null
            return true
        }

        info.toMove = LineRange(firstItem, lastItem)
        info.toMove.firstElement = firstItem
        info.toMove.lastElement = lastItem

        val target = findTargetElement(sibling, down)
        if (target == null) {
            info.toMove2 = null
            return true
        }
        info.toMove2 = LineRange(target)
        info.toMove2.firstElement = target
        return true
    }

    abstract protected fun findMovableAncestor(psi: PsiElement, endpoint: RangeEndpoint): PsiElement?
    abstract protected fun findTargetElement(sibling: PsiElement, down: Boolean): PsiElement?
    open protected fun fixupSibling(sibling: PsiElement, down: Boolean): PsiElement? = sibling

    companion object {
        enum class RangeEndpoint {
            START, END
        }

        fun isMovingOutOfBraceBlock(sibling: PsiElement, down: Boolean): Boolean =
            sibling.elementType == (if (down) RsElementTypes.RBRACE else RsElementTypes.LBRACE)

        fun isMovingOutOfParenBlock(sibling: PsiElement, down: Boolean): Boolean =
            sibling.elementType == (if (down) RsElementTypes.RPAREN else RsElementTypes.LPAREN)
    }
}

object UpDownMoverTestMarks {
    val moveOutOfImpl = Testmark("moveOutOfImpl")
    val moveOutOfMatch = Testmark("moveOutOfMatch")
}
