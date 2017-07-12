/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.parentOfType

class RsStructItemMover : RsAbstractMover() {
    override fun collectedElement(element: PsiElement): Pair<PsiElement, List<Int>>? {
        val collectedElement = element.parentOfType<RsStructItem>() ?: return null
        return collectedElement to listOf(collectedElement.line, collectedElement.struct?.line).mapNotNull { it }
    }

    override val listOfContainers = listOf(
        RsFunction::class.java,
        RsModItem::class.java
    )
    override val jumpOver = listOf(
        RsFunction::class.java,
        RsTraitItem::class.java,
        RsImplItem::class.java,
        RsMacroItem::class.java,
        RsStructItem::class.java,
        RsMacroInvocation::class.java,
        RsExternCrateItem::class.java,
        RsUseItem::class.java,
        RsMod::class.java
    )
}
