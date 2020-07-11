/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.psi.PsiElement
import com.intellij.util.Query
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.filterQuery
import org.rust.openapiext.mapQuery

/**
 * Annotates trait declaration with an icon on the gutter that allows to jump to
 * its implementations.
 *
 * See [org.rust.ide.navigation.goto.RsImplsSearch]
 */
class RsImplsLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: SlowRunMarketResult) {
        for (el in elements) {
            // Ideally, we want to avoid showing an icon if there are no implementations,
            // but that might be costly. To save time, we always show an icon, but calculate
            // the actual icons only when the user clicks it.
            // if (query.isEmptyQuery) return null
            val query = implsQuery(el) ?: continue
            val targets: NotNullLazyValue<Collection<PsiElement>> = NotNullLazyValue.createValue { query.findAll() }
            val info = NavigationGutterIconBuilder
                .create(RsIcons.IMPLEMENTED)
                .setTargets(targets)
                .setPopupTitle("Go to implementation of ${el.text}")
                .setTooltipText("Has implementations")
                .setCellRenderer(GoToImplRenderer())
                .createLineMarkerInfo(el)

            result.add(info)
        }
    }

    private class GoToImplRenderer : DefaultPsiElementCellRenderer() {
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

    companion object {
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
            return query.filterQuery(Condition { it != null })
        }
    }
}
