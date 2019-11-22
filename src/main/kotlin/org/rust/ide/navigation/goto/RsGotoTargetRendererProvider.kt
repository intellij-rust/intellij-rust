/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.navigation.goto

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.codeInsight.navigation.GotoTargetRendererProvider
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.RsAbstractable
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.owner

class RsGotoTargetRendererProvider : GotoTargetRendererProvider {
    override fun getRenderer(element: PsiElement, gotoData: GotoTargetHandler.GotoData): PsiElementListCellRenderer<*>? {
        val targetsInImpl = gotoData.targets.all { (it as? RsAbstractable)?.owner is RsAbstractableOwner.Impl }
        if (!gotoData.hasDifferentNames() && targetsInImpl) {
            return GoToImplRenderer()
        }
        if (element is RsImplItem) return ImplRenderer()
        return null
    }

    private class GoToImplRenderer : PsiElementListCellRenderer<RsAbstractable>() {
        override fun getContainerText(element: RsAbstractable, name: String?): String? = element.presentation?.locationString
        override fun getElementText(element: RsAbstractable): String? =
            (element.owner as RsAbstractableOwner.Impl).impl.presentation?.presentableText

        override fun getIconFlags(): Int = 0
    }


    private class ImplRenderer : PsiElementListCellRenderer<RsImplItem>() {
        override fun getContainerText(element: RsImplItem, name: String?): String? = element.presentation?.locationString
        override fun getElementText(element: RsImplItem): String? = element.presentation?.presentableText
        override fun getIconFlags(): Int = 0
    }
}
