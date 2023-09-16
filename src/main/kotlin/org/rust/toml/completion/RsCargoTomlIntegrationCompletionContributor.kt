/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionType.BASIC
import org.rust.lang.core.completion.RsCompletionProvider
import org.rust.toml.tomlPluginIsAbiCompatible

/** Provides completion in **Rust** files for elements that points to TOML elements, e.g. for cargo features */
class RsCargoTomlIntegrationCompletionContributor : CompletionContributor() {
    init {
        if (tomlPluginIsAbiCompatible()) {
            extend(BASIC, RsCfgFeatureCompletionProvider)
        }
    }

    @Suppress("SameParameterValue")
    private fun extend(type: CompletionType?, provider: RsCompletionProvider) {
        extend(type, provider.elementPattern, provider)
    }
}
