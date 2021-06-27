/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.target

import com.intellij.execution.target.getRuntimeType
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.titledRow

class RsLanguageRuntimeConfigurable(val config: RsLanguageRuntimeConfiguration) :
    BoundConfigurable(config.displayName, config.getRuntimeType().helpTopic) {

    override fun createPanel(): DialogPanel = panel {
        titledRow()
        row("Cargo executable:") {
            textField(config::cargoPath)
        }
        row("Version:") {
            textField(config::cargoVersion).enabled(false)
        }
    }
}
