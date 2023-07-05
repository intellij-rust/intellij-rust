/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.impl.RealPrefixMatchingWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.util.ProcessingContext
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.completion.nextCharIs
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.childOfType
import org.rust.lang.core.psi.ext.getPrevNonWhitespaceSibling
import org.rust.openapiext.isFeatureEnabled
import org.rust.stdext.unwrapOrElse
import org.rust.toml.StringValueInsertionHandler
import org.rust.toml.crates.local.CargoRegistryCrateVersion
import org.rust.toml.crates.local.CratesLocalIndexService
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.ext.name

class LocalCargoTomlDependencyCompletionProvider : TomlKeyValueCompletionProviderBase() {
    override fun completeKey(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val keySegment = keyValue.key.segments.singleOrNull() ?: return
        val prefix = CompletionUtil.getOriginalElement(keySegment)?.text ?: return

        val crateNames = CratesLocalIndexService.getInstance().getAllCrateNames().unwrapOrElse { return }

        val elements = crateNames.map { crateName ->
            PrioritizedLookupElement.withPriority(
                LookupElementBuilder
                    .create(crateName)
                    .withIcon(AllIcons.Nodes.PpLib)
                    .withInsertHandler(KeyInsertHandlerWithCompletion()),
                (-crateName.length).toDouble()
            )
        }
        result.withPrefixMatcher(CargoDependenciesPrefixMatcher(prefix)).addAllElements(elements)
    }

    override fun completeValue(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val keySegment = keyValue.key.segments.singleOrNull() ?: return
        val name = CompletionUtil.getOriginalElement(keySegment)?.text ?: return

        val crate = CratesLocalIndexService.getInstance().getCrate(name).unwrapOrElse { return }
        val sortedVersions = crate?.sortedVersions ?: return
        val elements = makeVersionCompletions(sortedVersions, keyValue)
        result.withRelevanceSorter(versionsSorter).addAllElements(elements)
    }
}

class LocalCargoTomlSpecificDependencyHeaderCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val keySegment = parameters.position.parent as? TomlKeySegment ?: return
        val prefix = CompletionUtil.getOriginalElement(keySegment)?.text ?: return
        val crateNames = CratesLocalIndexService.getInstance().getAllCrateNames().unwrapOrElse { return }

        val elements = crateNames.map { variant ->
            LookupElementBuilder.create(variant)
                .withIcon(AllIcons.Nodes.PpLib)
                .withInsertHandler { ctx, _ ->
                    val table = keySegment.ancestorStrict<TomlTable>() ?: return@withInsertHandler
                    if (table.entries.isEmpty()) {
                        ctx.document.insertString(
                            ctx.selectionEndOffset + 1,
                            "\nversion = \"\""
                        )

                        EditorModificationUtil.moveCaretRelatively(ctx.editor, 13)
                        AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                    }
                }
        }

        result.withPrefixMatcher(CargoDependenciesPrefixMatcher(prefix)).addAllElements(elements)
    }
}

class LocalCargoTomlSpecificDependencyVersionCompletionProvider : TomlKeyValueCompletionProviderBase() {
    override fun completeKey(keyValue: TomlKeyValue, result: CompletionResultSet) {
       result.addElement(
            LookupElementBuilder.create("version")
                .withInsertHandler(KeyInsertHandlerWithCompletion())
        )
    }

    override fun completeValue(keyValue: TomlKeyValue, result: CompletionResultSet) {
        if (keyValue.key.name != "version") return

       val dependencyNameKey = keyValue.getDependencyKey()
        val sortedVersions = CratesLocalIndexService.getInstance().getCrate(dependencyNameKey.text)
            .unwrapOrElse { return }
            ?.sortedVersions
            ?: return
        val elements = makeVersionCompletions(sortedVersions, keyValue)
        result.withRelevanceSorter(versionsSorter).addAllElements(elements)
    }
}

// FIXME: Use json schema for completing the whole Cargo.toml instead of hard-coding the parameter names and types
// https://json.schemastore.org/cargo.json
class CargoTomlDependencyKeysCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        for ((key, insertHandler) in dependencyKeys) {
            result.addElement(
                LookupElementBuilder.create(key)
                    .withInsertHandler(insertHandler)
            )
        }
    }

    private val defaultKeyInsertHandler = KeyInsertHandlerWithCompletion()

    private val dependencyKeys = mapOf(
        "branch" to defaultKeyInsertHandler,
        "default-features" to KeyInsertHandlerWithCompletion(" = ", 3),
        "features" to KeyInsertHandlerWithCompletion(" = []"),
        "git" to defaultKeyInsertHandler,
        "optional" to KeyInsertHandlerWithCompletion(" = ", 3),
        "package" to defaultKeyInsertHandler,
        "path" to defaultKeyInsertHandler,
        "registry" to defaultKeyInsertHandler,
        "registry" to defaultKeyInsertHandler,
        "rev" to defaultKeyInsertHandler,
        "tag" to defaultKeyInsertHandler,
    )
}

class LocalCargoTomlInlineTableVersionCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        if (!isFeatureEnabled(RsExperiments.CRATES_LOCAL_INDEX)) return

        val parent = parameters.position.parent ?: return
        val keyValue = parent.parent as? TomlKeyValue ?: return
        val dependencyNameKey = (keyValue.parent?.parent as? TomlKeyValue)?.key ?: return

        val sortedVersions = CratesLocalIndexService.getInstance().getCrate(dependencyNameKey.text)
            .unwrapOrElse { return }
            ?.sortedVersions
            ?: return
        val elements = makeVersionCompletions(sortedVersions, keyValue)
        result.withRelevanceSorter(versionsSorter).addAllElements(elements)
    }
}

private class KeyInsertHandlerWithCompletion(private val insertedValue: String = " = \"\"", private val caretShift: Int = 4) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val alreadyHasValue = context.nextCharIs('=')

        if (!alreadyHasValue) {
            context.document.insertString(context.selectionEndOffset, insertedValue)
        }

        EditorModificationUtil.moveCaretRelatively(context.editor, caretShift)

        if (!alreadyHasValue) {
            // Triggers dependency version completion
            AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor)
        }
    }
}

private fun makeVersionCompletions(sortedVersions: List<CargoRegistryCrateVersion>, keyValue: TomlKeyValue): List<LookupElement> {
    return sortedVersions.mapIndexed { index, variant ->
        val lookupElement = LookupElementBuilder.create(variant.version)
            .withInsertHandler(StringValueInsertionHandler(keyValue))
            .withTailText(if (variant.isYanked) " yanked" else null)
        PrioritizedLookupElement.withPriority(lookupElement, index.toDouble())
    }
}

private val versionsSorter: CompletionSorter = CompletionSorter.emptySorter()
    .weigh(RealPrefixMatchingWeigher())
    .weigh(object : LookupElementWeigher("priority", true, false) {
        override fun weigh(element: LookupElement): Double = (element as PrioritizedLookupElement<*>).priority
    })

fun TomlKeyValue.getDependencyKey(): TomlKeySegment {
    val tableDependency = (this.parent as? TomlTable)?.header?.key?.segments?.lastOrNull()
    return tableDependency
        ?: (parent as? TomlInlineTable)?.getPrevNonWhitespaceSibling()?.getPrevNonWhitespaceSibling()?.childOfType<TomlKeySegment>()
        ?: error("PsiElementPattern must not allow keys outside of TomlTable or TomlInlineTable")
}
