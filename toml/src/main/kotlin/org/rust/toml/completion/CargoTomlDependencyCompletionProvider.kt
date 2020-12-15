/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.util.ProcessingContext
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled

class CargoTomlDependencyCompletionProvider : CompletionProvider<CompletionParameters>() {

    private val localCompletionProvider = LocalCargoTomlDependencyCompletionProvider()
    private val cratesIoCompletionProvider = CratesIoCargoTomlDependencyCompletionProvider()

    private val delegate: TomlKeyValueCompletionProviderBase
        get() {
            return if (isFeatureEnabled(RsExperiments.CRATES_LOCAL_INDEX)) {
                localCompletionProvider
            } else {
                cratesIoCompletionProvider
            }
        }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        delegate.addCompletionVariants(parameters, context, result)
    }
}
