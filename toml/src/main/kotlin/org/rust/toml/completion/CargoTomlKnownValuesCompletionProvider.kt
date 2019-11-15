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
import org.rust.toml.StringValueInsertionHandler
import org.rust.toml.getClosestKeyValueAncestor

class CargoTomlKnownValuesCompletionProvider(private val knownValues: List<String>) : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val keyValue = getClosestKeyValueAncestor(parameters.position) ?: return
        result.addAllElements(knownValues.map { LookupElementBuilder.create(it).withInsertHandler(StringValueInsertionHandler(keyValue)) })
    }
}
