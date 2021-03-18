/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.completion.impl.RealPrefixMatchingWeigher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorModificationUtil
import org.rust.lang.core.completion.nextCharIs
import org.rust.toml.StringValueInsertionHandler
import org.rust.toml.crates.local.CratesLocalIndexService
import org.toml.lang.psi.TomlKeyValue

class LocalCargoTomlDependencyCompletionProvider : TomlKeyValueCompletionProviderBase() {
    override fun completeKey(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val prefix = CompletionUtil.getOriginalElement(keyValue.key)?.text ?: return

        val indexService = CratesLocalIndexService.getInstance()

        val crateNames = indexService.getAllCrateNames()
        val elements = crateNames.mapNotNull { crateName ->
            PrioritizedLookupElement.withPriority(
                LookupElementBuilder
                    .create(crateName)
                    .withIcon(AllIcons.Nodes.PpLib)
                    .withInsertHandler { ctx, _ ->
                        val alreadyHasValue = ctx.nextCharIs('=')

                        if (!alreadyHasValue) {
                            ctx.document.insertString(ctx.selectionEndOffset, " = \"\"")
                        }

                        EditorModificationUtil.moveCaretRelatively(ctx.editor, 4)

                        if (!alreadyHasValue) {
                            // Triggers dependency version completion
                            AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                        }
                    },
                (-crateName.length).toDouble()
            )
        }
        result.withPrefixMatcher(CargoDependenciesPrefixMatcher(prefix)).addAllElements(elements)
    }

    override fun completeValue(keyValue: TomlKeyValue, result: CompletionResultSet) {
        val name = CompletionUtil.getOriginalElement(keyValue.key)?.text ?: return

        val indexService = CratesLocalIndexService.getInstance()

        val versions = indexService.getCrate(name)?.sortedVersions ?: return
        val elements = versions.mapIndexed { index, variant ->
            val lookupElement = LookupElementBuilder.create(variant.version)
                .withInsertHandler(StringValueInsertionHandler(keyValue))
                .withTailText(if (variant.isYanked) " yanked" else null)

            PrioritizedLookupElement.withPriority(
                lookupElement,
                index.toDouble()
            )
        }
        val sorter = CompletionSorter.emptySorter()
            .weigh(RealPrefixMatchingWeigher())
            .weigh(object : LookupElementWeigher("priority", true, false) {
                override fun weigh(element: LookupElement): Double = (element as PrioritizedLookupElement<*>).priority
            })

        result.withRelevanceSorter(sorter).addAllElements(elements)
    }
}

