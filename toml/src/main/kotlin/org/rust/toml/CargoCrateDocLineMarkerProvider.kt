/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.util.io.URLUtil
import org.rust.cargo.CargoConstants
import org.rust.ide.icons.RsIcons
import org.rust.ide.lineMarkers.RsLineMarkerInfoUtils
import org.rust.lang.core.psi.ext.elementType
import org.toml.lang.psi.*

class CargoCrateDocLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
        if (!tomlPluginIsAbiCompatible()) return
        val firstElement = elements.firstOrNull() ?: return
        val file = firstElement.containingFile
        if (!file.name.equals(CargoConstants.MANIFEST_FILE, ignoreCase = true)) return

        loop@ for (element in elements) {
            val parent = element.parent
            if (parent is TomlKeySegment) {
                val keyValue = parent.parent?.parent as? TomlKeyValue ?: continue@loop
                val table = keyValue.parent as? TomlTable ?: continue@loop
                if (!table.header.isDependencyListHeader) continue@loop
                if (parent.firstChild?.nextSibling != null) continue@loop
                val pkgName = keyValue.crateName
                val pkgVersion = keyValue.version ?: continue@loop
                result += genLineMarkerInfo(element, pkgName, pkgVersion)
            } else if (element.elementType == TomlElementTypes.L_BRACKET) {
                val header = parent as? TomlTableHeader ?: continue@loop
                val names = header.key?.segments.orEmpty()
                if (names.getOrNull(names.size - 2)?.isDependencyKey != true) continue@loop
                val table = parent.parent as? TomlTable ?: continue@loop
                val version = table.entries.find { it.name == "version" }?.value?.stringValue ?: continue@loop
                result += genLineMarkerInfo(element, names.last().text, version)
            }
        }
    }

    private fun genLineMarkerInfo(anchor: PsiElement, name: String, version: String): LineMarkerInfo<PsiElement> {
        val urlVersion = when {
            version.isEmpty() -> "*"
            version.first().isDigit() -> "^${version}"
            else -> version
        }

        return RsLineMarkerInfoUtils.create(
            anchor,
            anchor.textRange,
            RsIcons.DOCS_MARK,
            { _, _ -> BrowserUtil.browse("https://docs.rs/$name/${URLUtil.encodeURIComponent(urlVersion)}") },
            GutterIconRenderer.Alignment.LEFT
        ) { "Open documentation for `$name@$urlVersion`" }
    }
}

private val TomlKeyValue.name get() = key.text
private val TomlKeyValue.crateName: String
    get() {
        return when (val rootValue = value) {
            is TomlInlineTable -> (rootValue.entries.find { it.name == "package" }?.value?.stringValue)
                ?: key.text
            else -> key.text
        }
    }
private val TomlKeyValue.version: String?
    get() {
        return when (val value = value) {
            is TomlLiteral -> value.stringValue
            is TomlInlineTable -> value.entries.find { it.name == "version" }?.value?.stringValue
            else -> null
        }
    }
