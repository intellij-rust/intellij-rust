/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import org.rust.openapiext.CheckboxDelegate
import javax.swing.JComponent

class RsDebuggerSettingsConfigurable(
    private val settings: RsDebuggerSettings
) : SearchableConfigurable {

    private val isRendersEnabledCheckbox: JBCheckBox = JBCheckBox("Enable Rust library renders")
    private var isRendersEnabled: Boolean by CheckboxDelegate(isRendersEnabledCheckbox)

    override fun getId(): String = "Debugger.Rust"
    override fun getDisplayName(): String = DISPLAY_NAME

    override fun createComponent(): JComponent = panel {
        row { isRendersEnabledCheckbox() }
    }

    override fun isModified(): Boolean = isRendersEnabled != settings.isRendersEnabled

    override fun apply() {
        settings.isRendersEnabled = isRendersEnabled
    }

    override fun reset() {
        isRendersEnabled = settings.isRendersEnabled
    }

    companion object {
        const val DISPLAY_NAME: String = "Rust"
    }
}
