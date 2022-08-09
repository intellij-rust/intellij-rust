/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Panel
import org.rust.debugger.RsDebuggerBundle

class RsBreakOnPanicConfigurableUi : RsDebuggerUiComponent() {
    private val breakOnPanicCheckBox: JBCheckBox
        = JBCheckBox(RsDebuggerBundle.message("settings.rust.debugger.break.on.panic.checkbox"), RsDebuggerSettings.getInstance().breakOnPanic)

    override fun reset(settings: RsDebuggerSettings) {
        breakOnPanicCheckBox.isSelected = settings.breakOnPanic
    }

    override fun isModified(settings: RsDebuggerSettings): Boolean
        = settings.breakOnPanic != breakOnPanicCheckBox.isSelected

    override fun apply(settings: RsDebuggerSettings) {
        settings.breakOnPanic = breakOnPanicCheckBox.isSelected
    }

    override fun buildUi(panel: Panel) {
        with(panel) {
            row { cell(breakOnPanicCheckBox) }
        }
    }
}
