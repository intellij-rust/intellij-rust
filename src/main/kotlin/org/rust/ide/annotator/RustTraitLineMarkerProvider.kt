package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
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
class RustTraitLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
        for (el in elements) {
            if (el !is RustTraitItemElement) continue
            val targets = ReferencesSearch.search(el, el.useScope)
                .map { it.element.parent?.parent }
                .filter { it is RustImplItemElement && it.type != null }

            if (!targets.isEmpty()) {
                val builder = NavigationGutterIconBuilder
                    .create(RustIcons.IMPLEMENTED)
                    .setTargets(targets)
                    .setPopupTitle("Go to implementation")
                    .setTooltipText("Has implementations")
                result.add(builder.createLineMarkerInfo(el.trait))
            }
        }
    }
}
