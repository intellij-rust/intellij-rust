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
import org.rust.lang.core.psi.ext.RsNamedElement

class RsGotoTargetRendererProvider : GotoTargetRendererProvider {
    override fun getRenderer(element: PsiElement, gotoData: GotoTargetHandler.GotoData): PsiElementListCellRenderer<*>? {
        return when (element) {
            is RsImplItem,
            is RsAbstractable -> NamedElementRenderer()
            else -> null
        }
    }

    // The default renderer just shows the element name
    // This would not be helpful in many cases
    // So replace it with our own renderer
    // TODO reword...
    private class NamedElementRenderer : PsiElementListCellRenderer<RsNamedElement>() {
        override fun getContainerText(element: RsNamedElement, name: String?): String? = element.presentation?.locationString
        override fun getElementText(element: RsNamedElement): String? = element.presentation?.presentableText
        override fun getIconFlags(): Int = 0
    }
}
