/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.rust.ide.actions.mover.RsLineMover.Companion.RangeEndpoint
import org.rust.lang.core.psi.RsElementTypes.BLOCK
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsMatchArm
import org.rust.lang.core.psi.RsMatchBody
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getPrevNonCommentSibling

class RsMatchArmUpDownMover : RsLineMover() {
    override fun findMovableAncestor(psi: PsiElement, endpoint: RangeEndpoint): PsiElement? {
        if (RsStatementUpDownMover.isMovableElement(psi)) return null
        return psi.ancestorOrSelf<RsMatchArm>()
    }

    override fun canApply(firstMovableElement: PsiElement, secondMovableElement: PsiElement): Boolean {
        val firstMatchBody = firstMovableElement.ancestorStrict<RsMatchBody>() ?: return false
        val secondMatchBody = secondMovableElement.ancestorStrict<RsMatchBody>() ?: return false
        // We shouldn't apply this mover for match arms from different match expressions
        return firstMatchBody == secondMatchBody
    }

    override fun findTargetElement(sibling: PsiElement, down: Boolean): PsiElement? {
        if (isMovingOutOfBraceBlock(sibling, down)) {
            UpDownMoverTestMarks.MoveOutOfMatch.hit()
            return null
        }
        return sibling
    }

    override fun beforeMove(editor: Editor, info: MoveInfo, down: Boolean) {
        val project = editor.project!!
        val psiFactory = RsPsiFactory(project)
        val matchArms = listOfNotNull(info.toMove.firstElement, info.toMove.lastElement, info.toMove2.firstElement)
            .filterIsInstance<RsMatchArm>()
        for (matchArm in matchArms) {
            val matchBody = matchArm.parent as? RsMatchBody
            if (matchBody != null &&
                matchBody.lastChild.getPrevNonCommentSibling() == matchArm &&
                matchArm.lastChild.elementType != COMMA &&
                matchArm.expr?.lastChild?.elementType != BLOCK
            ) {
                matchBody.addAfter(psiFactory.createComma(), matchArm)
            }
        }
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
    }
}
