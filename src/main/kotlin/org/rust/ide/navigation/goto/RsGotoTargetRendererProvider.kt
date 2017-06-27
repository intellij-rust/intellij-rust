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

class RsGotoTargetRendererProvider : GotoTargetRendererProvider {
    override fun getRenderer(element: PsiElement, gotoData: GotoTargetHandler.GotoData): PsiElementListCellRenderer<*>? {
        if (element is RsImplItem) return ImplRenderer()
        return null
    }

    private class ImplRenderer : PsiElementListCellRenderer<RsImplItem>() {
        override fun getContainerText(element: RsImplItem, name: String?): String? = element.presentation?.locationString
        override fun getElementText(element: RsImplItem): String? = element.presentation?.presentableText
        override fun getIconFlags(): Int = 0
    }
}
