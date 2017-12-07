/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsValueArgumentList
import org.rust.lang.core.psi.RsValueParameter
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getNextNonCommentSibling

class RsParamOrArgUpDownMover : RsLineMover() {
    private val listElementsToMove: MutableList<PsiElement> = mutableListOf()

    override fun findMovableAncestor(psi: PsiElement, endpoint: RangeEndpoint): PsiElement? {
        @Suppress("NAME_SHADOWING")
        val psi = if (endpoint == RangeEndpoint.END && psi.elementType == COMMA) {
            psi.prevSibling
        } else {
            psi
        }

        return findListElement(psi)?.also { listElementsToMove += it }
    }

    private fun findListElement(psi: PsiElement): PsiElement? {
        psi.ancestorOrSelf<RsValueParameter>()?.let { return it }
        var prev: PsiElement? = null
        for (a in psi.ancestors) {
            if (a is RsValueArgumentList && prev is RsExpr) return prev
            prev = a
        }
        return null
    }

    override fun fixupSibling(sibling: PsiElement, down: Boolean): PsiElement? {
        return if (sibling.elementType == COMMA && down) {
            val fixed = StatementUpDownMover.firstNonWhiteElement(sibling.nextSibling, true)
            if (fixed is RsValueParameter || fixed is RsExpr) {
                listElementsToMove += fixed
            }
            return fixed
        } else {
            sibling
        }
    }

    override fun findTargetLineRange(sibling: PsiElement, down: Boolean): LineRange? {
        if (isMovingOutOfParenBlock(sibling, down)) return null
        return LineRange(sibling)
    }

    override fun beforeMove(editor: Editor, info: MoveInfo, down: Boolean) {
        val project = editor.project!!
        val psiFactory = RsPsiFactory(project)
        for (element in listElementsToMove) {
            if (element.getNextNonCommentSibling()?.elementType != COMMA) {
                element.parent.addAfter(psiFactory.createComma(), element)
            }
        }
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
    }
}
