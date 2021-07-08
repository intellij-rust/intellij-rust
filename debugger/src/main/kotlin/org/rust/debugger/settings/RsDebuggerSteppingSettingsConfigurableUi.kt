/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.openapi.options.ConfigurableUi
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import org.rust.debugger.RsDebuggerBundle
import javax.swing.JComponent

class RsDebuggerSteppingSettingsConfigurableUi : ConfigurableUi<RsDebuggerSettings> {
    private val filterStdlib = JBCheckBox(
        RsDebuggerBundle.message("settings.rust.debugger.do.not.step.into.stdlib.checkbox"),
        RsDebuggerSettings.getInstance().stepSettings.filterStdlib
    )

    override fun isModified(settings: RsDebuggerSettings): Boolean {
        return filterStdlib.isSelected != settings.stepSettings.filterStdlib
    }

    override fun reset(settings: RsDebuggerSettings) {
        filterStdlib.isSelected = settings.stepSettings.filterStdlib
    }

    override fun apply(settings: RsDebuggerSettings) {
        settings.stepSettings = RsStepFilterSettings(filterStdlib.isSelected)
    }

    override fun getComponent(): JComponent {
        return panel {
            row { filterStdlib() }
        }
    }
}
