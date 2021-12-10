/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.ui.layout.panel
import org.rust.debugger.RsDebuggerToolchainService
import org.rust.debugger.RsDebuggerToolchainService.LLDBStatus
import javax.swing.JComponent

class RsDebuggerGeneralSettingsConfigurableUi : ConfigurableUi<RsDebuggerSettings>, Disposable {
    private val needsToolchainSettings: Boolean
        get() {
            val status = RsDebuggerToolchainService.getInstance().getLLDBStatus()
            // If there is bundled LLDB, no need to show this toolchain settings
            return status !is LLDBStatus.Bundled
        }

    private val components: List<RsDebuggerUiComponent> = run {
        val components = mutableListOf<RsDebuggerUiComponent>()
        if (needsToolchainSettings) {
            components.add(RsDebuggerToolchainConfigurableUi())
        }
        components.add(RsBreakOnPanicConfigurableUi())
        components
    }

    override fun isModified(settings: RsDebuggerSettings): Boolean = components.any { it.isModified(settings) }

    override fun reset(settings: RsDebuggerSettings) {
        components.forEach { it.reset(settings) }
    }

    override fun apply(settings: RsDebuggerSettings) {
        components.forEach { it.apply(settings) }
    }

    override fun getComponent(): JComponent {
        return panel {
            for (component in components) {
                component.buildUi(this)
            }
        }
    }

    override fun dispose() {
        components.forEach { it.dispose() }
    }
}
