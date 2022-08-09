/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.rust.ide.actions.mover.RsLineMover.Companion.RangeEndpoint
import org.rust.ide.formatter.impl.CommaList
import org.rust.ide.formatter.processors.addTrailingCommaForElement
import org.rust.ide.formatter.processors.isOnSameLineAsLastElement
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsMatchArm
import org.rust.lang.core.psi.ext.ancestorPairs
import org.rust.lang.core.psi.ext.elementType

class RsCommaListElementUpDownMover : RsLineMover() {
    override fun findMovableAncestor(psi: PsiElement, endpoint: RangeEndpoint): PsiElement? {
        if (psi is RsMatchArm) return null

        @Suppress("NAME_SHADOWING")
        val psi = if (endpoint == RangeEndpoint.END && psi.elementType == COMMA) {
            psi.prevSibling
        } else {
            psi
        }

        return findListElement(psi)
    }

    private fun findListElement(psi: PsiElement): PsiElement? {
        for ((child, parent) in psi.ancestorPairs) {
            // Stop at block expressions inside comma-separated lists
            if (parent is RsBlockExpr) return null
            val list = CommaList.forElement(parent.elementType)
            if (list != null && list.isElement(child)) return child
        }
        return null
    }

    override fun fixupSibling(sibling: PsiElement, down: Boolean): PsiElement? {
        return if (sibling.elementType == COMMA) {
            StatementUpDownMover.firstNonWhiteElement(if (down) sibling.nextSibling else sibling.prevSibling, down)
        } else {
            sibling
        }
    }

    override fun findTargetElement(sibling: PsiElement, down: Boolean): PsiElement? {
        if (isMovingOutOfParenBlock(sibling, down) ||
            isMovingOutOfBraceBlock(sibling, down) ||
            isMovingOutOfBracketBlock(sibling, down)
        ) {
            UpDownMoverTestMarks.MoveOutOfBlock.hit()
            return null
        }
        return sibling
    }

    override fun beforeMove(editor: Editor, info: MoveInfo, down: Boolean) {
        val project = editor.project!!
        for (element in listOfNotNull(info.toMove.firstElement, info.toMove.lastElement, info.toMove2.firstElement)) {
            val list = element.parent
            val commaList = CommaList.forElement(list.elementType) ?: continue
            if (commaList.isOnSameLineAsLastElement(list, element)) {
                commaList.addTrailingCommaForElement(list)
            }
        }
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
    }
}
