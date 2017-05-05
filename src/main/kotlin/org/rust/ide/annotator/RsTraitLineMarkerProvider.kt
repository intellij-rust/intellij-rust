package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.psi.PsiElement
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.searchForImplementations
import org.rust.lang.utils.isEmptyQuery

/**
 * Annotates trait declaration with an icon on the gutter that allows to jump to
 * its implementations.
 */
class RsTraitLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
        for (el in elements) {
            if (el !is RsTraitItem) continue
            val targets = el.searchForImplementations()
            if (targets.isEmptyQuery) continue

            val info = NavigationGutterIconBuilder
                .create(RsIcons.IMPLEMENTED)
                .setTargets(targets.findAll())
                .setPopupTitle("Go to implementation")
                .setTooltipText("Has implementations")
                .createLineMarkerInfo(el.trait)

            result.add(info)
        }
    }
}
