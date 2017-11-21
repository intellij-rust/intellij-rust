/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import javax.swing.Icon

/**
 * Annotates the implementation of a trait method with an icon on the gutter.
 */
class RsTraitMethodImplLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(el: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>) {
        if (!(el is RsFunction && el.owner.isTraitImpl)) return

        val traitMethod = el.superMethod ?: return
        val trait = traitMethod.ancestorStrict<RsTraitItem>() ?: return

        val action: String
        val icon: Icon
        if (traitMethod.isAbstract) {
            action = "Implements"
            icon = RsIcons.IMPLEMENTING_METHOD
        } else {
            action = "Overrides"
            icon = RsIcons.OVERRIDING_METHOD
        }

        val builder = NavigationGutterIconBuilder
            .create(icon)
            .setTargets(listOf(traitMethod))
            .setTooltipText("$action method in `${trait.name}`")

        result.add(builder.createLineMarkerInfo(el.fn))
    }
}
