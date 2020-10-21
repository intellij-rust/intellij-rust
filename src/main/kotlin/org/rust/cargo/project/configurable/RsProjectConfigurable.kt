/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.panel
import org.rust.cargo.project.settings.RustProjectSettingsService.MacroExpansionEngine
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import javax.swing.ListCellRenderer

class RsProjectConfigurable(
    project: Project
) : RsConfigurableBase(project, "Rust"), Configurable.NoScroll {
    private val rustProjectSettings: RustProjectSettingsPanel = RustProjectSettingsPanel(project)

    override fun createPanel(): DialogPanel = panel {
        rustProjectSettings.attachTo(this)
        row("Expand declarative macros:") {
            comboBox(
                EnumComboBoxModel(MacroExpansionEngine::class.java),
                state::macroExpansionEngine,
                createExpansionEngineListRenderer()
            ).comment(
                "Allow plugin to process declarative macro invocations to extract information for name resolution and type inference"
            )
        }
        row {
            checkBox("Inject Rust language into documentation comments", state::doctestInjectionEnabled)
        }
    }

    private fun createExpansionEngineListRenderer(): ListCellRenderer<MacroExpansionEngine?> {
        return SimpleListCellRenderer.create("") {
            when (it) {
                MacroExpansionEngine.DISABLED -> "Disable (select only if you have problems with macro expansion)"
                MacroExpansionEngine.OLD -> "Use old engine (some features are not supported) "
                MacroExpansionEngine.NEW -> "Use new engine"
                null -> error("Unreachable")
            }
        }
    }

    override fun disposeUIResources() {
        super.disposeUIResources()
        Disposer.dispose(rustProjectSettings)
    }

    override fun reset() {
        super.reset()
        rustProjectSettings.sdk = state.sdk
    }

    override fun isModified(): Boolean {
        if (super.isModified()) return true
        return rustProjectSettings.sdk != state.sdk
    }

    @Throws(ConfigurationException::class)
    override fun doApply() {
        rustProjectSettings.validateSettings(sdkRequired = false)
        state.sdk = rustProjectSettings.sdk
    }
}
