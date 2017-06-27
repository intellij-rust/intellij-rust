/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.execution.filters.BrowserHyperlinkInfo
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.ext.containingCargoPackage

/**
 * Provides an external crate imports with gutter icons that open documentation on docs.rs.
 */
class RsCrateDocLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
        for (el in elements) {
            val crateItem = el as? RsExternCrateItem ?: continue
            val crateName = crateItem.identifier.text
            val crate = crateItem.containingCargoPackage?.findCrateByName(crateName) ?: continue
            if (crate.pkg.source == null) continue
            result.add(LineMarkerInfo(
                crateItem.crate,
                crateItem.crate.textRange,
                RsIcons.DOCS_MARK,
                Pass.LINE_MARKERS,
                { "Open documentation for `${crate.pkg.normName}`" },
                { _, _ -> BrowserHyperlinkInfo.openUrl("https://docs.rs/${crate.pkg.name}/${crate.pkg.version}/${crate.normName}") },
                GutterIconRenderer.Alignment.LEFT))
        }
    }
}
