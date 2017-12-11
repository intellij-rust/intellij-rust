/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMatchArm
import org.rust.lang.core.psi.ext.ancestorOrSelf

class RsMatchArmUpDownMover : RsLineMover() {
    override fun findMovableAncestor(psi: PsiElement, endpoint: RangeEndpoint): PsiElement? =
        psi.ancestorOrSelf<RsMatchArm>()

    override fun findTargetElement(sibling: PsiElement, down: Boolean): PsiElement? {
        if (isMovingOutOfBraceBlock(sibling, down)) {
            UpDownMoverTestMarks.moveOutOfMatch.hit()
            return null
        }
        return sibling
    }
}
