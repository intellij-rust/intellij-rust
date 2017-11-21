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
    override fun findMovableAncestor(psi: PsiElement): PsiElement? =
        psi.ancestorOrSelf<RsMatchArm>()

    override fun findTargetLineRange(sibling: PsiElement, down: Boolean): LineRange? {
        if (isMovingOutOfBlock(sibling, down)) {
            UpDownMoverTestMarks.moveOutOfMatch.hit()
            return null
        }
        return LineRange(sibling)
    }
}
