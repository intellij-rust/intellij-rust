/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.LayoutBuilder
import org.rust.debugger.RsDebuggerBundle

class RsEnableRustMSVCConfigurableUi : RsDebuggerUiComponent() {
    private val enableRustMSVCCheckBox: JBCheckBox
        = JBCheckBox(RsDebuggerBundle.message("settings.rust.debugger.enable.rust.msvc.checkbox"), RsDebuggerSettings.getInstance().enableRustMSVC)

    override fun reset(settings: RsDebuggerSettings) {
        enableRustMSVCCheckBox.isSelected = settings.enableRustMSVC
    }

    override fun isModified(settings: RsDebuggerSettings): Boolean
        = settings.enableRustMSVC != enableRustMSVCCheckBox.isSelected

    override fun apply(settings: RsDebuggerSettings) {
        settings.enableRustMSVC = enableRustMSVCCheckBox.isSelected
    }

    override fun buildUi(builder: LayoutBuilder) {
        with(builder) {
            row { enableRustMSVCCheckBox() }
        }
    }
}
