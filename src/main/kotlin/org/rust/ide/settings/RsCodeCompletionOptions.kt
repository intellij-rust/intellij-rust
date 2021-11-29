/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.settings

import com.intellij.application.options.CodeCompletionOptionsCustomSection
import com.intellij.openapi.options.ConfigurableBuilder
import org.rust.RsBundle

class RsCodeCompletionConfigurable : ConfigurableBuilder(RsBundle.message("settings.rust.completion.title")),
                                     CodeCompletionOptionsCustomSection {
    init {
        checkBox(
            RsBundle.message("settings.rust.completion.suggest.out.of.scope.items"),
            RsCodeInsightSettings.getInstance()::suggestOutOfScopeItems
        )
    }
}
