/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.codeInsight.hints.InlayParameterHintsExtension
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.Label
import com.intellij.ui.layout.panel
import com.intellij.util.PlatformUtils
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.util.modulesWithCargoProject
import org.rust.lang.RsLanguage
import org.rust.utils.pathAsPath
import java.nio.file.Paths
import javax.swing.JComponent

class RustProjectConfigurable(
    private val project: Project
) : Configurable, Configurable.NoScroll {

    private val rustProjectSettings = RustProjectSettingsPanel(
        rustModule?.cargoProjectRoot?.pathAsPath ?: Paths.get(".")
    )
    private val autoUpdateEnabledCheckbox = JBCheckBox()
    private val useCargoCheckForBuildCheckbox = JBCheckBox()
    private val useCargoCheckAnnotatorCheckbox = JBCheckBox()

    private val hintCheckboxes: MutableMap<String, JBCheckBox> = mutableMapOf()
    private val hintProvider = InlayParameterHintsExtension.forLanguage(RsLanguage)

    private val cargoTomlLocation = Label("N/A")

    private var autoUpdateEnabled: Boolean
        get() = autoUpdateEnabledCheckbox.isSelected
        set(value) {
            autoUpdateEnabledCheckbox.isSelected = value
        }

    private var useCargoCheckForBuild: Boolean
        get() = useCargoCheckForBuildCheckbox.isSelected
        set(value) {
            useCargoCheckForBuildCheckbox.isSelected = value
        }

    private var useCargoCheckAnnotator: Boolean
        get() = useCargoCheckAnnotatorCheckbox.isSelected
        set(value) {
            useCargoCheckAnnotatorCheckbox.isSelected = value
        }

    override fun createComponent(): JComponent = panel {
        rustProjectSettings.attachTo(this)
        row(label = Label("Watch Cargo.toml:")) { autoUpdateEnabledCheckbox() }
        if (PlatformUtils.isIntelliJ()) {
            row("Use cargo check when build project:") { useCargoCheckForBuildCheckbox() }
        }
        row(label = Label("Use cargo check to analyze code:")) { useCargoCheckAnnotatorCheckbox() }
        row("Cargo.toml") { cargoTomlLocation() }

        var first = true
        for ((id, name) in hintProvider.supportedOptions) {
            val checkbox = JBCheckBox()
            row(label = Label("$name:"), separated = first) { checkbox() }
            hintCheckboxes.put(id, checkbox)
            first = false
        }
    }

    override fun disposeUIResources() = Disposer.dispose(rustProjectSettings)

    override fun reset() {
        val settings = project.rustSettings
        val toolchain = settings.toolchain ?: RustToolchain.suggest()

        rustProjectSettings.data = RustProjectSettingsPanel.Data(
            toolchain = toolchain,
            explicitPathToStdlib = settings.explicitPathToStdlib
        )
        autoUpdateEnabled = settings.autoUpdateEnabled
        useCargoCheckForBuild = settings.useCargoCheckForBuild
        useCargoCheckAnnotator = settings.useCargoCheckAnnotator

        for (option in hintProvider.supportedOptions) {
            val checkbox = hintCheckboxes.get(option.id) ?: continue
            checkbox.isSelected = option.get()
        }

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

        for (option in hintProvider.supportedOptions) {
            val checkbox = hintCheckboxes.get(option.id) ?: continue
            option.set(checkbox.isSelected)
        }

        val settings = project.rustSettings
        settings.data = RustProjectSettingsService.Data(
            toolchain = rustProjectSettings.data.toolchain,
            explicitPathToStdlib = rustProjectSettings.data.explicitPathToStdlib,
            autoUpdateEnabled = autoUpdateEnabled,
            useCargoCheckForBuild = useCargoCheckForBuild,
            useCargoCheckAnnotator = useCargoCheckAnnotator
        )
    }

    override fun isModified(): Boolean {
        val settings = project.rustSettings
        val data = rustProjectSettings.data
        for (option in hintProvider.supportedOptions) {
            val checkbox = hintCheckboxes[option.id] ?: continue
            if (checkbox.isSelected != option.get()) {
                return true
            }
        }
        return data.toolchain?.location != settings.toolchain?.location
            || data.explicitPathToStdlib != settings.explicitPathToStdlib
            || autoUpdateEnabled != settings.autoUpdateEnabled
            || useCargoCheckForBuild != settings.useCargoCheckForBuild
            || useCargoCheckAnnotator != settings.useCargoCheckAnnotator
    }

    override fun getDisplayName(): String = "Rust" // sync me with plugin.xml

    override fun getHelpTopic(): String? = null

    private val rustModule: Module? get() = project.modulesWithCargoProject.firstOrNull()
}

