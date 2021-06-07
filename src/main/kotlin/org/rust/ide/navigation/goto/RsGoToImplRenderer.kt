/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.ext.RsAbstractable
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.owner

class RsGoToImplRenderer : DefaultPsiElementCellRenderer() {
    override fun getElementText(element: PsiElement?): String {
        return super.getElementText(getTarget(element))
    }

    override fun getContainerText(element: PsiElement?, name: String?): String? {
        return super.getContainerText(getTarget(element), name)
    }

    private fun getTarget(element: PsiElement?): PsiElement? = when (element) {
        is RsAbstractable -> when (val owner = element.owner) {
            is RsAbstractableOwner.Impl -> owner.impl
            is RsAbstractableOwner.Trait -> owner.trait
            else -> element
        }
        else -> element
    }
}
