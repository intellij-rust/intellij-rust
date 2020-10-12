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
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.RustProjectSettingsService.MacroExpansionEngine
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.cargo.toolchain.RsToolchain
import org.rust.openapiext.pathAsPath
import java.nio.file.Paths
import javax.swing.ListCellRenderer

class RsProjectConfigurable(
    project: Project
) : RsConfigurableBase(project, "Rust"), Configurable.NoScroll {

    private val rustProjectSettings = RustProjectSettingsPanel(
        project.cargoProjects.allProjects.firstOrNull()?.rootDir?.pathAsPath ?: Paths.get(".")
    )

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
        val toolchain = state.toolchain ?: RsToolchain.suggest()

        rustProjectSettings.data = RustProjectSettingsPanel.Data(
            toolchain = toolchain,
            explicitPathToStdlib = state.explicitPathToStdlib
        )
    }

    override fun isModified(): Boolean {
        if (super.isModified()) return true
        val data = rustProjectSettings.data
        return data.toolchain?.location != state.toolchain?.location
            || data.explicitPathToStdlib != state.explicitPathToStdlib
    }

    @Throws(ConfigurationException::class)
    override fun doApply() {
        rustProjectSettings.validateSettings()
        state.toolchain = rustProjectSettings.data.toolchain
        state.explicitPathToStdlib = rustProjectSettings.data.explicitPathToStdlib
    }
}
