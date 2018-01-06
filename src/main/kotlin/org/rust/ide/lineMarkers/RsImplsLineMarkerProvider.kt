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
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.searchForImplementations
import org.rust.lang.core.psi.ext.union
import org.rust.openapiext.mapQuery

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
        fun implsQuery(psi: PsiElement): Query<RsItemElement>? {
            val parent = psi.parent
            // Necessary because RsImplItem contains a RsMembers which contains the actual items
            val parent3 = parent.parent?.parent
            return when  {
                // For performance reasons (see LineMarkerProvider.getLineMarkerInfo)
                // we need to add the line marker only to leaf elements
                parent is RsTraitItem && parent.identifier == psi -> parent.searchForImplementations().mapQuery { it }
                parent is RsConstant && parent.identifier == psi && parent3 is RsTraitItem &&
                    parent3.members?.constantList?.contains(parent) ?: false -> {
                    parent.searchForImplementations()
                }
                parent is RsFunction && parent.identifier == psi && parent3 is RsTraitItem &&
                    parent3.members?.functionList?.contains(parent) ?: false -> {
                    parent.searchForImplementations()
                }
                parent is RsTypeAlias && parent.identifier == psi && parent3 is RsTraitItem &&
                    parent3.members?.typeAliasList?.contains(parent) ?: false -> {
                    parent.searchForImplementations()
                }
                parent is RsStructItem && parent.identifier == psi ->
                    parent.searchForImplementations().mapQuery { it }
                parent is RsEnumItem && parent.identifier == psi -> parent.searchForImplementations().mapQuery { it }
                else -> return null
            }
        }
    }
}
