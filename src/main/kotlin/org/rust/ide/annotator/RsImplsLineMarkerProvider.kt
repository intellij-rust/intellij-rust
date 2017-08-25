/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

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
            val (query, anchor) = implsQuery(el) ?: continue
            val targets: NotNullLazyValue<Collection<PsiElement>> = NotNullLazyValue.createValue { query.findAll() }
            val info = NavigationGutterIconBuilder
                .create(RsIcons.IMPLEMENTED)
                .setTargets(targets)
                .setPopupTitle("Go to implementation")
                .setTooltipText("Has implementations")
                .createLineMarkerInfo(anchor)

            result.add(info)
        }
    }

    companion object {
        fun implsQuery(psi: PsiElement): Pair<Query<RsImplItem>, PsiElement>? {
            val (query, anchor) = when (psi) {
                is RsTraitItem -> psi.searchForImplementations() to psi.trait
                is RsStructItem -> psi.searchForImplementations() to (psi.struct ?: psi.union)!!
                is RsEnumItem -> psi.searchForImplementations() to psi.enum
                else -> return null
            }
            // Ideally, we want to avoid showing an icon if there are no implementations,
            // but that might be costly. To save time, we always show an icon, but calculate
            // the actual icons only when the user clicks it.
            // if (query.isEmptyQuery) return null
            return query to anchor
        }
    }

}
