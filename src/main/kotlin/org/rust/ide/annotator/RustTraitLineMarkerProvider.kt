package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustTraitItemElement

/**
 * Annotates trait declaration with an icon on the gutter that allows to jump to
 * its implementations.
 */
class RustTraitLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(el: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>?) {
        if (result == null || el !is RustTraitItemElement) return
        val targets = ReferencesSearch.search(el, el.useScope)
            .map { it.element.parent?.parent }
            .filter { it is RustImplItemElement && it.type != null }
            .toList()

        if (!targets.isEmpty()) {
            val builder = NavigationGutterIconBuilder
                .create(RustIcons.IMPLEMENTED)
                .setTargets(targets)
                .setPopupTitle("Go to implementation")
                .setTooltipText("Has implementations")
            result.add(builder.createLineMarkerInfo(el))
        }
    }
}
