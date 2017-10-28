/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.psi.PsiElement
import com.intellij.util.Query
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.searchForImplementations
import org.rust.lang.core.psi.ext.union

/**
 * Annotates trait declaration with an icon on the gutter that allows to jump to
 * its implementations.
 *
 * See [org.rust.ide.navigation.goto.RsImplsSearch]
 */
class RsImplsLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
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
                .setPopupTitle("Go to implementation")
                .setTooltipText("Has implementations")
                .createLineMarkerInfo(el)

            result.add(info)
        }
    }

    companion object {
        fun implsQuery(psi: PsiElement): Query<RsImplItem>? {
            val parent = psi.parent
            return when  {
                parent is RsTraitItem && parent.trait == psi -> parent.searchForImplementations()
                parent is RsStructItem && (parent.struct == psi || parent.union == psi) ->
                    parent.searchForImplementations()
                parent is RsEnumItem && parent.enum == psi -> parent.searchForImplementations()
                else -> return null
            }
        }
    }
}
