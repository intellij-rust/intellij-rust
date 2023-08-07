/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.psi.PsiElement
import com.intellij.util.Query
import org.jetbrains.annotations.TestOnly
import org.rust.RsBundle
import org.rust.ide.icons.RsIcons
import org.rust.ide.navigation.goto.RsGoToImplRenderer
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.filterQuery
import org.rust.openapiext.mapQuery
import javax.swing.Icon

/**
 * Annotates trait declaration with an icon on the gutter that allows to jump to
 * its implementations.
 *
 * See [org.rust.ide.navigation.goto.RsImplsSearch]
 */
class RsImplsLineMarkerProvider : LineMarkerProviderDescriptor() {

    override fun getName(): String = RsBundle.message("gutter.rust.implemented.item.name")
    override fun getIcon(): Icon = RsIcons.IMPLEMENTED

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        for (el in elements) {
            // Ideally, we want to avoid showing an icon if there are no implementations,
            // but that might be costly. To save time, we always show an icon, but calculate
            // the actual icons only when the user clicks it.
            // if (query.isEmptyQuery) return null
            val query = implsQuery(el) ?: continue
            val targets: NotNullLazyValue<Collection<PsiElement>> = NotNullLazyValue.createValue { query.findAll() }
            val info = ImplsGutterIconBuilder(el.text, icon)
                .setTargets(targets)
                .setTooltipText(RsBundle.message("gutter.rust.implemented.item.tooltip"))
                .setCellRenderer { RsGoToImplRenderer() }
                .createLineMarkerInfo(el)

            result.add(info)
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
