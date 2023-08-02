/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.docs.getExternalDocumentationBaseUrl
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsExternCrateItem
import org.rust.lang.core.psi.ext.containingCargoPackage
import javax.swing.Icon

/**
 * Provides an external crate imports with gutter icons that open documentation on docs.rs.
 */
class RsCrateDocLineMarkerProvider : LineMarkerProviderDescriptor() {

    override fun getName(): String = RsBundle.message("gutter.rust.open.documentation.name")
    override fun getIcon(): Icon = RsIcons.DOCS_MARK

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement>? {
        val parent = element.parent
        if (!(parent is RsExternCrateItem && parent.crate == element)) return null
        val crateName = parent.name ?: return null
        val crate = parent.containingCargoPackage?.findDependency(crateName) ?: return null
        if (crate.pkg.source == null) return null

        val baseUrl = getExternalDocumentationBaseUrl()

        return RsLineMarkerInfoUtils.create(
            element,
            element.textRange,
            icon,
            { _, _ -> BrowserUtil.browse("$baseUrl${crate.pkg.name}/${crate.pkg.version}/${crate.normName}") },
            GutterIconRenderer.Alignment.LEFT
        ) { RsBundle.message("gutter.rust.open.documentation.for", crate.pkg.normName) }
    }
}
