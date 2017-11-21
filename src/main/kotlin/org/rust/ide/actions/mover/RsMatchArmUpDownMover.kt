/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsMatchArm
import org.rust.lang.core.psi.ext.ancestorStrict

class RsMatchArmUpDownMover : RsStatementUpDownMover() {
    override fun collectedElement(element: PsiElement): Pair<PsiElement, List<Int>>? {
        val collectedElement = element.ancestorStrict<RsMatchArm>() ?: return null
        return collectedElement to listOf(collectedElement.line, collectedElement.matchArmGuard?.line).mapNotNull { it }
    }
    override val containers = listOf(
        RsElementTypes.MATCH_BODY,
        RsElementTypes.MATCH_EXPR,
        RsElementTypes.MATCH
    )
    override val jumpOver = listOf(
        RsElementTypes.MATCH_ARM
    )
}
