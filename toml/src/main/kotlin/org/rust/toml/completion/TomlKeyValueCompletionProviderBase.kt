/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.util.ProcessingContext
import org.rust.toml.getClosestKeyValueAncestor
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeySegment

abstract class TomlKeyValueCompletionProviderBase : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val parent = parameters.position.parent ?: return
        if (parent is TomlKeySegment) {
            val keyValue = parent.parent?.parent as? TomlKeyValue
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
