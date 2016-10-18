package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustImplMethodMemberElement
import org.rust.lang.core.psi.RustTraitItemElement
import org.rust.lang.core.psi.util.parentOfType
import javax.swing.Icon

/**
 * Annotates the implementation of a trait method with an icon on the gutter.
 */
class RustTraitMethodImplLineMarkerProvider : RelatedItemLineMarkerProvider() {
    override fun collectNavigationMarkers(el: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>?) {
        if (result == null || el !is RustImplMethodMemberElement) return
        val implBlock = el.parentOfType<RustImplItemElement>() ?: return
        val trait = implBlock.traitRef?.path?.reference?.resolve() ?: return
        if (trait !is RustTraitItemElement) return
        val traitMethod = trait.traitMethodMemberList.find { it.name == el.name } ?: return
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
        result.add(builder.createLineMarkerInfo(el))
    }
}
