package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.execution.filters.BrowserHyperlinkInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.cargoProject
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.util.module

/**
 * Provides an external crate imports with gutter icons that open documentation on docs.rs.
 */
class RsCrateDocLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
        for (el in elements) {
            val crateItem = el as? RsExternCrateItem ?: continue
            val cargoProject = crateItem.module?.cargoProject ?: continue
            val crate = cargoProject.findCrateByName(crateItem.identifier.text) ?: continue
            if (crate.pkg.source == null) continue
            result.add(LineMarkerInfo(
                crateItem.crate,
                crateItem.crate.textRange,
                RsIcons.DOCS_MARK,
                // TODO: change the `6` to Pass.LINE_MARKERS, when it does not duplicate icons.
                6,
                { "Open documentation for `${crate.normName}`" },
                { e, c ->
                    BrowserHyperlinkInfo.openUrl("https://docs.rs/${crate.pkg.name}/${crate.pkg.version}/${crate.normName}")
                },
                GutterIconRenderer.Alignment.LEFT)
            )
        }
    }
}
