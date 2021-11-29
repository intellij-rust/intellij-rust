/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.settings

import com.intellij.application.options.CodeCompletionOptionsCustomSection
import com.intellij.openapi.options.ConfigurableBuilder

class RsCodeCompletionConfigurable : ConfigurableBuilder("Rust"), CodeCompletionOptionsCustomSection {
    init {
        checkBox("Suggest out of scope items", RsCodeInsightSettings.getInstance()::suggestOutOfScopeItems)
    }
}
