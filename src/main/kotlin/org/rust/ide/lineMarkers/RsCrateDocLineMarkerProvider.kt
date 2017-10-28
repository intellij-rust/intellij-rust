/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.ext.containingCargoPackage

/**
 * Provides an external crate imports with gutter icons that open documentation on docs.rs.
 */
class RsCrateDocLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? {
        val parent = element.parent
        if (!(parent is RsExternCrateItem && parent.crate == element)) return null
        val crateName = parent.name ?: return null
        val crate = parent.containingCargoPackage?.findDependency(crateName) ?: return null
        if (crate.pkg.source == null) return null

        return LineMarkerInfo(
            element,
            element.textRange,
            RsIcons.DOCS_MARK,
            Pass.LINE_MARKERS,
            { "Open documentation for `${crate.pkg.normName}`" },
            { _, _ -> BrowserUtil.browse("https://docs.rs/${crate.pkg.name}/${crate.pkg.version}/${crate.normName}") },
            GutterIconRenderer.Alignment.LEFT
        )
    }

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
    }
}
