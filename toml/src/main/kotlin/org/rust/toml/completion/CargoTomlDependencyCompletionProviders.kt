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

class CargoTomlDependencyCompletionProvider : CargoTomlCompletionProviderDelegate() {
    override val localCompletionProvider = LocalCargoTomlDependencyCompletionProvider()
    override val cratesIoCompletionProvider = CratesIoCargoTomlDependencyCompletionProvider()
}

class CargoTomlSpecificDependencyHeaderCompletionProvider : CargoTomlCompletionProviderDelegate() {
    override val localCompletionProvider = LocalCargoTomlSpecificDependencyHeaderCompletionProvider()
    override val cratesIoCompletionProvider = CratesIoCargoTomlSpecificDependencyHeaderCompletionProvider()
}

class CargoTomlSpecificDependencyVersionCompletionProvider : CargoTomlCompletionProviderDelegate() {
    override val localCompletionProvider = LocalCargoTomlSpecificDependencyVersionCompletionProvider()
    override val cratesIoCompletionProvider = CratesIoCargoTomlSpecificDependencyVersionCompletionProvider()
}

abstract class CargoTomlCompletionProviderDelegate : CompletionProvider<CompletionParameters>() {
    protected abstract val localCompletionProvider : CompletionProvider<CompletionParameters>
    protected abstract val cratesIoCompletionProvider : CompletionProvider<CompletionParameters>

    private val delegate: CompletionProvider<CompletionParameters>
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
