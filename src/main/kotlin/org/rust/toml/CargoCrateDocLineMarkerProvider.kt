/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import org.rust.ide.icons.RsIcons
import org.toml.lang.psi.*

class CargoCrateDocLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: MutableList<PsiElement>,
        result: MutableCollection<LineMarkerInfo<PsiElement>>
    ) {
        if (!tomlPluginIsAbiCompatible()) return
        for (element in elements) {
            val file = element.containingFile
            if (file.name.toLowerCase() != "cargo.toml") continue
            if (element !is TomlTable) continue
            result += annotateTable(element)
        }
    }

    private fun annotateTable(table: TomlTable): Collection<LineMarkerInfo<PsiElement>> {
        val names = table.header.names
        val test = names.getOrNull(names.size - 2)
        val lastName = names.lastOrNull() ?: return emptyList()
        if (test?.isDependencyKey == true) {
            val version = table.entries.find { it.name == "version" }?.value?.text?.trimVersion
            val lineMarkerInfo = genLineMarkerInfo(table.header.names.first(), lastName.text, version)
                ?: return emptyList()
            return listOf(lineMarkerInfo)
        }
        if (!lastName.isDependencyKey) return emptyList()
        return table.entries.mapNotNull {
            val pkgName = it.name
            val pkgVersion = it.version
            genLineMarkerInfo(it.key, pkgName, pkgVersion)
        }
    }

    private fun genLineMarkerInfo(element: TomlKey, name: String, version: String?): LineMarkerInfo<PsiElement>? {
        @Suppress("NAME_SHADOWING")
        val version = version ?: return null
        val anchor = element.bareKey
        return LineMarkerInfo(
            anchor,
            anchor.textRange,
            RsIcons.DOCS_MARK,
            Pass.LINE_MARKERS,
            { "Open documentation for `$name@$version`" },
            { _, _ -> BrowserUtil.browse("https://docs.rs/$name/$version/$name") },
            GutterIconRenderer.Alignment.LEFT)

    }
}

private val TomlKey.bareKey get() = firstChild
private val TomlKeyValue.name get() = key.text
private val TomlKeyValue.version: String?
    get() {
        val value = value
        return when (value) {
            is TomlLiteral -> value.text?.trimVersion
            is TomlInlineTable -> value.entries.find { it.name == "version" }?.value?.text?.trimVersion
            else -> null
        }
    }

private val String.trimVersion get() = this.removePrefix("\"").removeSuffix("\"")
