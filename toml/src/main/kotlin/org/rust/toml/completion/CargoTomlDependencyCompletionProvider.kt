/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.util.ProcessingContext
import com.intellij.util.text.SemVer
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.toml.StringValueInsertionHandler
import org.rust.toml.crates.CrateDescription
import org.rust.toml.crates.CrateResolverService
import org.rust.toml.crates.crateResolver
import org.rust.toml.getClosestKeyValueAncestor
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

abstract class TomlKeyValueCompletionProviderBase : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val parent = parameters.position.parent ?: return
        if (parent is TomlKey) {
            val keyValue = parent.parent as? TomlKeyValue
                ?: error("PsiElementPattern must not allow keys outside of TomlKeyValues")
            completeKey(keyValue, result)
        } else {
            val keyValue = getClosestKeyValueAncestor(parameters.position) ?: return
            completeValue(keyValue, result)
        }
    }

    protected abstract fun completeKey(keyValue: TomlKeyValue, result: CompletionResultSet)

    protected abstract fun completeValue(keyValue: TomlKeyValue, result: CompletionResultSet)
}

/** @see CargoTomlPsiPattern.inDependencyKeyValue */
class CargoTomlDependencyCompletionProvider : TomlKeyValueCompletionProviderBase() {
    override fun completeKey(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val resolver = getCrateResolver(keyValue.project) ?: return
        val variants = resolver.searchCrates(getCrateName(keyValue.key)).map { it.dependencyLine }
        result.addAllElements(variants.map(LookupElementBuilder::create))
    }

    override fun completeValue(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val version = getCrateLastVersion(keyValue.key) ?: return

        result.addElement(
            LookupElementBuilder.create(version)
                .withInsertHandler(StringValueInsertionHandler(keyValue))
        )
    }
}

/** @see CargoTomlPsiPattern.inSpecificDependencyHeaderKey */
class CargoTomlSpecificDependencyHeaderCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val key = parameters.position.parent as? TomlKey ?: return
        val resolver = getCrateResolver(key.project) ?: return
        val variants = resolver.searchCrates(getCrateName(key))

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
class CargoTomlSpecificDependencyVersionCompletionProvider : TomlKeyValueCompletionProviderBase() {
    override fun completeKey(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val dependencyNameKey = getDependencyKeyFromTableHeader(keyValue)

        val version = getCrateLastVersion(dependencyNameKey) ?: return
        result.addElement(LookupElementBuilder.create("version = \"$version\""))
    }

    override fun completeValue(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val dependencyNameKey = getDependencyKeyFromTableHeader(keyValue)
        val version = getCrateLastVersion(dependencyNameKey) ?: return

        result.addElement(
            LookupElementBuilder.create(version)
                .withInsertHandler(StringValueInsertionHandler(keyValue))
        )
    }

    private fun getDependencyKeyFromTableHeader(keyValue: TomlKeyValue): TomlKey {
        val table = keyValue.parent as? TomlTable
            ?: error("PsiElementPattern must not allow keys outside of TomlTable")
        return table.header.names.lastOrNull()
            ?: error("PsiElementPattern must not allow KeyValues in tables without header")
    }
}

private fun getCrateResolver(project: Project): CrateResolverService? {
    val resolver = project.crateResolver
    if (!resolver.isAvailable()) return null
    return resolver
}

private fun getCrateLastVersion(key: TomlKey): SemVer? {
    val resolver = getCrateResolver(key.project) ?: return null
    return resolver.getCrate(getCrateName(key))?.maxVersion
}

private fun getCrateName(key: TomlKey): String = CompletionUtil.getOriginalElement(key)?.text ?: ""

private val CrateDescription.dependencyLine: String
    get() = "$name = \"$maxVersion\""
