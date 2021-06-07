/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.codeInsight.navigation.NavigationGutterIconRenderer
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.Query
import org.jetbrains.annotations.TestOnly
import org.rust.ide.icons.RsIcons
import org.rust.ide.navigation.goto.RsGoToImplRenderer
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.filterQuery
import org.rust.openapiext.mapQuery
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.Icon

/**
 * Annotates trait declaration with an icon on the gutter that allows to jump to
 * its implementations.
 *
 * See [org.rust.ide.navigation.goto.RsImplsSearch]
 */
class RsImplsLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (el in elements) {
            // Ideally, we want to avoid showing an icon if there are no implementations,
            // but that might be costly. To save time, we always show an icon, but calculate
            // the actual icons only when the user clicks it.
            // if (query.isEmptyQuery) return null
            val query = implsQuery(el) ?: continue
            val targets: NotNullLazyValue<Collection<PsiElement>> = NotNullLazyValue.createValue { query.findAll() }
            val info = ImplsGutterIconBuilder(RsIcons.IMPLEMENTED)
                .setTargets(targets)
                .setTooltipText("Has implementations")
                .setCellRenderer(RsGoToImplRenderer())
                .createLineMarkerInfo(el)

            result.add(info)
        }
    }

    private class ImplsGutterIconBuilder(icon: Icon) :
        NavigationGutterIconBuilder<PsiElement>(icon, DEFAULT_PSI_CONVERTOR, PSI_GOTO_RELATED_ITEM_PROVIDER) {

        override fun createGutterIconRenderer(
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
                elt.putUserData(RENDERED_IMPLS, renderedItems)
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

    companion object {
        @TestOnly
        val RENDERED_IMPLS: Key<List<String>> = Key.create("RENDERED_IMPLS")

        fun implsQuery(psi: PsiElement): Query<RsElement>? {
            val parent = psi.parent
            val query: Query<RsElement> = when {
                // For performance reasons (see LineMarkerProvider.getLineMarkerInfo)
                // we need to add the line marker only to leaf elements
                parent is RsTraitItem && parent.identifier == psi -> parent.searchForImplementations().mapQuery { it }
                parent is RsStructItem && parent.identifier == psi -> parent.searchForImplementations().mapQuery { it }
                parent is RsEnumItem && parent.identifier == psi -> parent.searchForImplementations().mapQuery { it }
                parent is RsAbstractable &&
                    parent.identifyingElement == psi &&
                    parent.owner is RsAbstractableOwner.Trait -> parent.searchForImplementations().mapQuery { it }
                else -> return null
            }
            return query.filterQuery { it != null }
        }
    }
}
