/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.codeInsight.navigation.NavigationGutterIconRenderer
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.rust.openapiext.isUnitTestMode
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.Icon

abstract class ImplsGutterIconBuilderBase(icon: Icon) :
    NavigationGutterIconBuilder<PsiElement>(icon, DEFAULT_PSI_CONVERTOR, PSI_GOTO_RELATED_ITEM_PROVIDER) {

    protected fun createGutterIconRendererInner(
        pointers: NotNullLazyValue<List<SmartPsiElementPointer<*>>>,
        renderer: Computable<PsiElementListCellRenderer<*>>,
        empty: Boolean
    ): NavigationGutterIconRenderer {
        return ImplsNavigationGutterIconRenderer(
            popupTitle = myPopupTitle,
            emptyText = myEmptyText,
            pointers = pointers,
            cellRenderer = renderer,
            alignment = myAlignment,
            icon = myIcon,
            tooltipText = myTooltipText,
            empty = empty
        )
    }

    private class ImplsNavigationGutterIconRenderer(
        popupTitle: String?,
        emptyText: String?,
        pointers: NotNullLazyValue<List<SmartPsiElementPointer<*>>>,
        cellRenderer: Computable<PsiElementListCellRenderer<*>>,
        private val alignment: Alignment,
        private val icon: Icon,
        private val tooltipText: String?,
        private val empty: Boolean
    ) : NavigationGutterIconRenderer(popupTitle, emptyText, cellRenderer, pointers) {
        override fun isNavigateAction(): Boolean = !empty
        override fun getIcon(): Icon = icon
        override fun getTooltipText(): String? = tooltipText
        override fun getAlignment(): Alignment = alignment

        override fun navigate(event: MouseEvent?, elt: PsiElement?) {
            if (event == null || elt == null) return

            val targets = targetElements.filterIsInstance<NavigatablePsiElement>().toTypedArray()
            val renderer = myCellRenderer.compute()

            Arrays.sort(targets, Comparator.comparing(renderer::getComparingObject))

            if (isUnitTestMode) {
                val renderedItems = targets.map(renderer::getElementText)
                elt.putUserData(RsImplsLineMarkerProvider.RENDERED_IMPLS, renderedItems)
            } else {
                val escapedName = StringUtil.escapeXmlEntities(elt.text)
                @Suppress("DialogTitleCapitalization")
                PsiElementListNavigator.openTargets(
                    event,
                    targets,
                    CodeInsightBundle.message("goto.implementation.chooserTitle", escapedName, targets.size, ""),
                    CodeInsightBundle.message("goto.implementation.findUsages.title", escapedName, targets.size),
                    renderer
                )
            }
        }
    }
}
