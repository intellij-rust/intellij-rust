/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import com.intellij.psi.PsiElement
import org.rust.ide.actions.mover.RsLineMover.Companion.RangeEndpoint
import org.rust.lang.core.psi.RsMatchArm
import org.rust.lang.core.psi.RsMatchBody
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.ancestorStrict

class RsMatchArmUpDownMover : RsLineMover() {
    override fun findMovableAncestor(psi: PsiElement, endpoint: RangeEndpoint): PsiElement? =
        psi.ancestorOrSelf<RsMatchArm>()

    override fun canApply(firstMovableElement: PsiElement, secondMovableElement: PsiElement): Boolean {
        val firstMatchBody = firstMovableElement.ancestorStrict<RsMatchBody>() ?: return false
        val secondMatchBody = secondMovableElement.ancestorStrict<RsMatchBody>() ?: return false
        // We shouldn't apply this mover for match arms from different match expressions
        return firstMatchBody == secondMatchBody
    }

    override fun findTargetElement(sibling: PsiElement, down: Boolean): PsiElement? {
        if (isMovingOutOfBraceBlock(sibling, down)) {
            UpDownMoverTestMarks.moveOutOfMatch.hit()
            return null
        }
        return sibling
    }
}
