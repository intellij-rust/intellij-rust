/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.navigation.NavigationGutterIconRenderer
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.psi.SmartPsiElementPointer
import javax.swing.Icon

class ImplsGutterIconBuilder(icon: Icon) : ImplsGutterIconBuilderBase(icon) {
    override fun createGutterIconRenderer(
        pointers: NotNullLazyValue<List<SmartPsiElementPointer<*>>>,
        renderer: Computable<PsiElementListCellRenderer<*>>,
        empty: Boolean,
    ): NavigationGutterIconRenderer {
        return createGutterIconRendererInner(pointers, renderer, empty)
    }
}
