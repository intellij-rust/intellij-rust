/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

// BACKCOMPAT: 2021.1
@file:Suppress("DEPRECATION")

package org.rust.ide.navigation.goto

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.codeInsight.navigation.GotoTargetRendererProvider
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.ext.RsAbstractable

class RsGotoTargetRendererProvider : GotoTargetRendererProvider {
    override fun getRenderer(
        element: PsiElement,
        gotoData: GotoTargetHandler.GotoData
    ): PsiElementListCellRenderer<*>? {
        return if (element is RsImplItem || element is RsAbstractable && !gotoData.hasDifferentNames()) {
            RsGoToImplRenderer()
        } else {
            null
        }
    }
}
