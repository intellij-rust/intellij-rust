/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsMembers
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.tokenSetOf


class RsItemUpDownMover : RsLineMover() {
    private val movableItems = tokenSetOf(
        RsElementTypes.TRAIT_ITEM,
        RsElementTypes.IMPL_ITEM,
        RsElementTypes.MACRO_CALL,
        RsElementTypes.MACRO_DEFINITION,
        RsElementTypes.STRUCT_ITEM,
        RsElementTypes.ENUM_ITEM,
        RsElementTypes.MOD_ITEM,
        RsElementTypes.USE_ITEM,
        RsElementTypes.FUNCTION,
        RsElementTypes.CONSTANT,
        RsElementTypes.TYPE_ALIAS
    )

    override fun findMovableAncestor(psi: PsiElement, endpoint: RangeEndpoint): PsiElement? =
        psi.ancestors.find { it.elementType in movableItems }

    override fun findTargetElement(sibling: PsiElement, down: Boolean): PsiElement? {
        if (isMovingOutOfBraceBlock(sibling, down) && sibling.parent is RsMembers) {
            UpDownMoverTestMarks.moveOutOfImpl.hit()
            return null
        }
        return sibling
    }
}
