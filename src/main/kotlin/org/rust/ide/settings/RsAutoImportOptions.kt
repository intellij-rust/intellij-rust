/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.settings

import com.intellij.application.options.editor.AutoImportOptionsProvider
import com.intellij.openapi.options.ConfigurableBuilder

class RsAutoImportOptions : ConfigurableBuilder("Rust"), AutoImportOptionsProvider {
    init {
        checkBox("Show import popup", RsCodeInsightSettings.getInstance()::showImportPopup)
        checkBox("Import out-of-scope items on completion", RsCodeInsightSettings.getInstance()::importOutOfScopeItems)
    }
}
