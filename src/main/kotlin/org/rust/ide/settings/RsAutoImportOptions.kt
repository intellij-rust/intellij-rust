/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.settings

import com.intellij.application.options.editor.AutoImportOptionsProvider
import com.intellij.openapi.options.ConfigurableBuilder
import org.rust.RsBundle

class RsAutoImportOptions : ConfigurableBuilder(RsBundle.message("settings.rust.auto.import.title")),
                            AutoImportOptionsProvider {
    init {
        checkBox(
            RsBundle.message("settings.rust.auto.import.show.popup"),
            RsCodeInsightSettings.getInstance()::showImportPopup
        )
        checkBox(
            RsBundle.message("settings.rust.auto.import.on.completion"),
            RsCodeInsightSettings.getInstance()::importOutOfScopeItems
        )
    }
}
