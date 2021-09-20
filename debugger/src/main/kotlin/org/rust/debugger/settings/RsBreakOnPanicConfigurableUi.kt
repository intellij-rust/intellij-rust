/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.LayoutBuilder

class RsBreakOnPanicConfigurableUi : RsDebuggerUiComponent() {
    private val breakOnPanicCheckBox: JBCheckBox
        = JBCheckBox("Break on panic", RsDebuggerSettings.getInstance().breakOnPanic)

    override fun reset(settings: RsDebuggerSettings) {
        breakOnPanicCheckBox.isSelected = settings.breakOnPanic
    }

    override fun isModified(settings: RsDebuggerSettings): Boolean
        = settings.breakOnPanic != breakOnPanicCheckBox.isSelected

    override fun apply(settings: RsDebuggerSettings) {
        settings.breakOnPanic = breakOnPanicCheckBox.isSelected
    }

    override fun buildUi(builder: LayoutBuilder) {
        with(builder) {
            row { breakOnPanicCheckBox() }
        }
    }
}
