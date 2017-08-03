/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.parentOfType

class RsStructItemUpDownMover : RsAbstractUpDownMover() {
    override fun collectedElement(element: PsiElement): Pair<PsiElement, List<Int>>? {
        val collectedElement = element.parentOfType<RsStructItem>() ?: return null
        return collectedElement to listOf(collectedElement.line, collectedElement.struct?.line).mapNotNull { it }
    }

    override val containers = listOf(
        FUNCTION,
        MOD_ITEM
    )

    override val jumpOver = listOf(
        FUNCTION,
        TRAIT_ITEM,
        IMPL_ITEM,
        MACRO_CALL,
        STRUCT_ITEM,
        MACRO_DEFINITION,
        EXTERN_CRATE_ITEM,
        USE_ITEM,
        MOD_ITEM
    )
}
