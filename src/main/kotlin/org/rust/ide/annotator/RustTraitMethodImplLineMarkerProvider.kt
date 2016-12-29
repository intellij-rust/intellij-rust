package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.psi.impl.mixin.RustFunctionKind
import org.rust.lang.core.psi.impl.mixin.isAbstract
import org.rust.lang.core.psi.impl.mixin.kind
import org.rust.lang.core.psi.impl.mixin.superMethod
import org.rust.lang.core.psi.util.parentOfType
import javax.swing.Icon

/**
 * Annotates the implementation of a trait method with an icon on the gutter.
 */
class RustTraitMethodImplLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(el: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>) {
        if (!(el is RustFunctionElement && el.kind == RustFunctionKind.IMPL_METHOD)) return

        val traitMethod = el.superMethod ?: return
        val trait = traitMethod.parentOfType<RustTraitItemElement>() ?: return

        val action: String
        val icon: Icon
        if (traitMethod.isAbstract) {
            action = "Implements"
            icon = RustIcons.IMPLEMENTING_METHOD
        } else {
            action = "Overrides"
            icon = RustIcons.OVERRIDING_METHOD
        }

        val builder = NavigationGutterIconBuilder
            .create(icon)
            .setTargets(listOf(traitMethod))
            .setTooltipText("$action method in `${trait.name}`")

        result.add(builder.createLineMarkerInfo(el.fn))
    }
}
