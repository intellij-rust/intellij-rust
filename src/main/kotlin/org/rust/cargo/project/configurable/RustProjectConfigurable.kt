/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.configurable

import com.intellij.codeInsight.hints.InlayParameterHintsExtension
import com.intellij.codeInsight.hints.Option
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.Label
import com.intellij.ui.layout.panel
import com.intellij.util.PlatformUtils
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.cargo.toolchain.RustToolchain
import org.rust.lang.RsLanguage
import org.rust.openapiext.CheckboxDelegate
import org.rust.openapiext.pathAsPath
import java.nio.file.Paths
import javax.swing.JComponent

class RustProjectConfigurable(
    private val project: Project
) : Configurable, Configurable.NoScroll {

    private val rustProjectSettings = RustProjectSettingsPanel(
        project.cargoProjects.allProjects.firstOrNull()?.rootDir?.pathAsPath ?: Paths.get(".")
    )

    private val autoUpdateEnabledCheckbox = JBCheckBox()
    private var autoUpdateEnabled: Boolean by CheckboxDelegate(autoUpdateEnabledCheckbox)

    private val useCargoCheckForBuildCheckbox = JBCheckBox()
    private var useCargoCheckForBuild: Boolean by CheckboxDelegate(useCargoCheckForBuildCheckbox)

    private val useCargoCheckAnnotatorCheckbox = JBCheckBox()
    private var useCargoCheckAnnotator: Boolean by CheckboxDelegate(useCargoCheckAnnotatorCheckbox)

    private val hintProvider = InlayParameterHintsExtension.forLanguage(RsLanguage)
    private val hintCheckboxes: Map<String, JBCheckBox> =
        hintProvider.supportedOptions.associate { it.id to JBCheckBox() }

    private fun checkboxForOption(opt: Option) = hintCheckboxes[opt.id]!!

    override fun createComponent(): JComponent = panel {
        rustProjectSettings.attachTo(this)
        row(label = Label("Watch Cargo.toml:")) { autoUpdateEnabledCheckbox() }
        if (PlatformUtils.isIntelliJ()) {
            row("Use cargo check when build project:") { useCargoCheckForBuildCheckbox() }
        }
        row(label = Label("Use cargo check to analyze code:")) { useCargoCheckAnnotatorCheckbox() }

        var first = true
        for (option in hintProvider.supportedOptions) {
            row(label = Label("${option.name}:"), separated = first) { checkboxForOption(option)() }
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
            checkboxForOption(option).isSelected = option.get()
        }
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        rustProjectSettings.validateSettings()

        for (option in hintProvider.supportedOptions) {
            option.set(checkboxForOption(option).isSelected)
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
        if (hintProvider.supportedOptions.any { checkboxForOption(it).isSelected != it.get() }) return true
        return data.toolchain?.location != settings.toolchain?.location
            || data.explicitPathToStdlib != settings.explicitPathToStdlib
            || autoUpdateEnabled != settings.autoUpdateEnabled
            || useCargoCheckForBuild != settings.useCargoCheckForBuild
            || useCargoCheckAnnotator != settings.useCargoCheckAnnotator
    }

    override fun getDisplayName(): String = "Rust" // sync me with plugin.xml

    override fun getHelpTopic(): String? = null
}
