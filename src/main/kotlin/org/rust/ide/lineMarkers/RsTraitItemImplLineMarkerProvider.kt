/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.RsAbstractable
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.nameLikeElement
import org.rust.lang.core.psi.ext.superItem
import javax.swing.Icon

/**
 * Annotates the implementation of a trait members (const, fn, type) with an icon on the gutter.
 */
class RsTraitItemImplLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(el: PsiElement, result: NavigationMarkersResult) {
        if (el !is RsAbstractable) return

        val superItem = el.superItem ?: return
        val trait = superItem.ancestorStrict<RsTraitItem>() ?: return

        val action: String
        val icon: Icon
        if (superItem.isAbstract) {
            action = "Implements"
            icon = RsIcons.IMPLEMENTING_METHOD
        } else {
            action = "Overrides"
            icon = RsIcons.OVERRIDING_METHOD
        }

        val (type, element) = when (el) {
            is RsConstant -> "constant" to el.nameLikeElement
            is RsFunction -> "method" to el.identifier
            is RsTypeAlias -> "type" to el.identifier
            else -> error("unreachable")
        }

        val builder = NavigationGutterIconBuilder
            .create(icon)
            .setTargets(listOf(superItem))
            .setTooltipText("$action $type in `${trait.name}`")

        result.add(builder.createLineMarkerInfo(element))
    }
}
