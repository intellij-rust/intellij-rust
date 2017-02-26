package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.RsFunctionRole
import org.rust.lang.core.psi.ext.isAbstract
import org.rust.lang.core.psi.ext.role
import org.rust.lang.core.psi.ext.superMethod
import org.rust.lang.core.psi.ext.parentOfType
import javax.swing.Icon

/**
 * Annotates the implementation of a trait method with an icon on the gutter.
 */
class RsTraitMethodImplLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(el: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>) {
        if (!(el is RsFunction && el.role == RsFunctionRole.IMPL_METHOD)) return

        val traitMethod = el.superMethod ?: return
        val trait = traitMethod.parentOfType<RsTraitItem>() ?: return

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
