/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

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
import org.rust.ide.hints.HintType
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
    private val letBindingHintCheckbox = JBCheckBox()
    private val parameterHintCheckbox = JBCheckBox()
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

    private var letBindingHint: Boolean
        get() = letBindingHintCheckbox.isSelected
        set(value) {
            letBindingHintCheckbox.isSelected = value
        }

    private var parameterHint: Boolean
        get() = parameterHintCheckbox.isSelected
        set(value) {
            parameterHintCheckbox.isSelected = value
        }

    override fun createComponent(): JComponent = panel {
        rustProjectSettings.attachTo(this)
        row(label = Label("Watch Cargo.toml:")) { autoUpdateEnabledCheckbox() }
        if (PlatformUtils.isIntelliJ()) {
            row("Use cargo check when build project:") { useCargoCheckForBuildCheckbox() }
        }
        row(label = Label("Use cargo check to analyze code:")) { useCargoCheckAnnotatorCheckbox() }
        row(label = Label("Show local variable type hints:")) { letBindingHintCheckbox() }
        row(label = Label("Show argument name hints:")) { parameterHintCheckbox() }
        row("Cargo.toml") { cargoTomlLocation() }
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
        letBindingHint = HintType.LET_BINDING_HINT.enabled
        parameterHint = HintType.PARAMETER_HINT.enabled

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
        HintType.LET_BINDING_HINT.option.set(letBindingHint)
        HintType.PARAMETER_HINT.option.set(parameterHint)
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
        return data.toolchain?.location != settings.toolchain?.location
            || data.explicitPathToStdlib != settings.explicitPathToStdlib
            || autoUpdateEnabled != settings.autoUpdateEnabled
            || useCargoCheckForBuild != settings.useCargoCheckForBuild
            || useCargoCheckAnnotator != settings.useCargoCheckAnnotator
            || letBindingHint != HintType.LET_BINDING_HINT.option.get()
            || parameterHint != HintType.PARAMETER_HINT.option.get()
    }

    override fun getDisplayName(): String = "Rust" // sync me with plugin.xml

    override fun getHelpTopic(): String? = null

    private val rustModule: Module? get() = project.modulesWithCargoProject.firstOrNull()
}

