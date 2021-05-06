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
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKeyValueOwner
import org.toml.lang.psi.TomlTable

class CargoTomlDependencyAttributeCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val table = parameters.position.ancestorOrSelf<TomlKeyValueOwner>() ?: return
        if (table !is TomlTable && table !is TomlInlineTable) return
        addUniqueKeyCompletion(DEPENDENCY_KEYS, table, result)
    }

    companion object {
        private val DEPENDENCY_KEYS = mapOf(
            "default-features" to "true",
            "features" to "[]",
            "git" to "\"\"",
            "optional" to "false",
            "package" to "\"\"",
            "path" to "\"\"",
            "registry" to "\"\""
        )
    }
}

private fun addUniqueKeyCompletion(
    keyToValue: Map<String, String>,
    table: TomlKeyValueOwner,
    result: CompletionResultSet
) {
    val existingKeys = table.entries.map { it.key.text }.toSet()
    for ((key, value) in keyToValue) {
        if (key !in existingKeys) {
            val prefix = "$key = "
            val completion = "$prefix$value"
            result.addElement(
                LookupElementBuilder
                    .create(completion)
                    .withInsertHandler { context, _ ->
                        var start = context.startOffset + prefix.length
                        var end = context.selectionEndOffset
                        if (value.startsWith("[") || value.startsWith("\"")) {
                            start += 1
                            end -= 1
                        }
                        context.editor.selectionModel.setSelection(start, end)
                        context.editor.caretModel.moveToOffset(start)
                    }
            )
        }
    }
}
