package org.rust.cargo.project.configurable

import com.intellij.openapi.module.Module
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.Label
import com.intellij.ui.layout.panel
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.util.modulesWithCargoProject
import javax.swing.JComponent

class RustProjectConfigurable(
    private val project: Project
) : Configurable, Configurable.NoScroll {

    private val rustProjectSettings = RustProjectSettingsPanel(rustModule?.cargoProjectRoot?.path ?: ".")
    private val cargoTomlLocation = Label("N/A")

    override fun createComponent(): JComponent = panel {
        rustProjectSettings.attachTo(this)
        row("Cargo.toml") { cargoTomlLocation() }
    }

    override fun disposeUIResources() = Disposer.dispose(rustProjectSettings)

    override fun reset() {
        val settings = project.rustSettings
        val toolchain = settings.toolchain ?: RustToolchain.suggest()

        rustProjectSettings.data = RustProjectSettingsPanel.Data(
            toolchain,
            settings.autoUpdateEnabled,
            settings.data.explicitPathToStdlib,
            settings.data.useCargoCheckForBuild,
            settings.data.useCargoCheckAnnotator
        )
        val module = rustModule

        if (module == null) {
            cargoTomlLocation.text = "N/A"
            cargoTomlLocation.foreground = JBColor.RED
        } else {
            val projectRoot = module.cargoProjectRoot

            if (projectRoot != null) {
                cargoTomlLocation.text = projectRoot.findChild(RustToolchain.CARGO_TOML)?.presentableUrl
            } else {
                cargoTomlLocation.text = "N/A"
            }

            cargoTomlLocation.foreground = JBColor.foreground()
        }
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        rustProjectSettings.validateSettings()
        val settings = project.rustSettings
        rustProjectSettings.data.applyTo(settings)
    }

    override fun isModified(): Boolean {
        val settings = project.rustSettings
        val data = rustProjectSettings.data
        return data.toolchain?.location != settings.toolchain?.location
            || data.autoUpdateEnabled != settings.autoUpdateEnabled
            || data.explicitPathToStdlib != settings.data.explicitPathToStdlib
            || data.useCargoCheckForBuild != settings.data.useCargoCheckForBuild
            || data.useCargoCheckAnnotator != settings.data.useCargoCheckAnnotator
    }

    override fun getDisplayName(): String = "Rust" // sync me with plugin.xml

    override fun getHelpTopic(): String? = null

    private val rustModule: Module? get() = project.modulesWithCargoProject.firstOrNull()
}

