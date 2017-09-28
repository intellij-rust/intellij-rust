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
import com.intellij.psi.util.PsiTreeUtil;
import org.rust.ide.icons.RsIcons
import org.toml.lang.psi.*

class CargoCrateDocLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement) = null

    override fun collectSlowLineMarkers(elements: MutableList<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
        if (!tomlPluginIsAbiCompatible()) return
        for (el in elements) {
            val file = el.containingFile
            if (file.name.toLowerCase() != "cargo.toml") continue
            if (el !is TomlTable) continue
            result += annotateTable(el)
        }
    }

    private fun annotateTable(el: TomlTable): Collection<LineMarkerInfo<PsiElement>> {
        val names = el.header.names.map { it.text }
        val test = names.getOrNull(names.size - 2)
        val lastName = names.lastOrNull() ?: return emptyList()
        if (test != null && (test == "dependencies" || test == "dev-dependencies" || test == "build-dependencies")) {
            val version = el.entries.find { it.name == "version" }?.value?.text?.trimVersion
            val lineMarkerInfo = genLineMarkerInfo(el.header.names.first(), lastName, version) ?: return emptyList()
            return listOf(lineMarkerInfo)
        }
        if (lastName != "dependencies" && lastName != "dev-dependencies" && lastName != "build-dependencies") return emptyList()
        return el.entries.mapNotNull {
            val pkgName = it.name
            val pkgVersion = it.version
            genLineMarkerInfo(it.key, pkgName, pkgVersion)
        }
    }

    private fun genLineMarkerInfo(element: TomlKey, name: String, version: String?): LineMarkerInfo<PsiElement>? {
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
