/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

abstract class RsDebuggerUiComponent: ConfigurableUi<RsDebuggerSettings>, Disposable {
    abstract fun buildUi(panel: Panel)

    override fun getComponent(): JComponent {
        return panel {
            buildUi(this)
        }
    }

    override fun dispose() {}
}
