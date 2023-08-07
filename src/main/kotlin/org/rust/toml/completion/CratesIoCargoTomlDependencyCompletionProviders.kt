/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.toml.CargoTomlPsiPattern
import org.rust.toml.StringValueInsertionHandler
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

/** @see CargoTomlPsiPattern.inDependencyKeyValue */
class CratesIoCargoTomlDependencyCompletionProvider : TomlKeyValueCompletionProviderBase() {
    override fun completeKey(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val key = keyValue.key.segments.singleOrNull() ?: return
        val variants = searchCrate(key).map { it.dependencyLine }
        result.addAllElements(variants.map(LookupElementBuilder::create))
    }

    override fun completeValue(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val key = keyValue.key.segments.singleOrNull() ?: return
        val version = getCrateLastVersion(key) ?: return

        result.addElement(
            LookupElementBuilder.create(version)
                .withInsertHandler(StringValueInsertionHandler(keyValue))
        )
    }
}

/** @see CargoTomlPsiPattern.inSpecificDependencyHeaderKey */
class CratesIoCargoTomlSpecificDependencyHeaderCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val key = parameters.position.parent as? TomlKeySegment ?: return
        val variants = searchCrate(key)

        val elements = variants.map { variant ->
            LookupElementBuilder.create(variant.name)
                .withPresentableText(variant.dependencyLine)
                .withInsertHandler { context, _ ->
                    val table = key.ancestorStrict<TomlTable>() ?: return@withInsertHandler
                    if (table.entries.isEmpty()) {
                        context.document.insertString(
                            context.selectionEndOffset + 1,
                            "\nversion = \"${variant.maxVersion}\""
                        )
                    }
                }
        }

        result.addAllElements(elements)
    }
}

/** @see CargoTomlPsiPattern.inSpecificDependencyKeyValue */
class CratesIoCargoTomlSpecificDependencyVersionCompletionProvider : TomlKeyValueCompletionProviderBase() {
    override fun completeKey(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val key = if (keyValue.value != null) {
            "version"
        } else {
            val dependencyNameKey = keyValue.getDependencyKey()

            val version = getCrateLastVersion(dependencyNameKey) ?: return
            "version = \"$version\""
        }
        result.addElement(LookupElementBuilder.create(key))
    }

    override fun completeValue(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val dependencyNameKey = keyValue.getDependencyKey()
        val version = getCrateLastVersion(dependencyNameKey) ?: return

        result.addElement(
            LookupElementBuilder.create(version)
                .withInsertHandler(StringValueInsertionHandler(keyValue))
        )
    }
}
