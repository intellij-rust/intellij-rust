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
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.cargo.toolchain.RustToolchain
import org.rust.ide.ui.layout
import org.rust.lang.RsLanguage
import org.rust.openapiext.CheckboxDelegate
import org.rust.openapiext.pathAsPath
import java.nio.file.Paths
import javax.swing.JComponent

class RsProjectConfigurable(
    project: Project
) : RsConfigurableBase(project), Configurable.NoScroll {

    private val rustProjectSettings = RustProjectSettingsPanel(
        project.cargoProjects.allProjects.firstOrNull()?.rootDir?.pathAsPath ?: Paths.get(".")
    )

    private val expandMacrosCheckbox: JBCheckBox = JBCheckBox()
    private var expandMacros: Boolean by CheckboxDelegate(expandMacrosCheckbox)

    private val hintProvider = InlayParameterHintsExtension.forLanguage(RsLanguage)
    private val hintCheckboxes: Map<String, JBCheckBox> =
        hintProvider.supportedOptions.associate { it.id to JBCheckBox() }

    override fun getDisplayName(): String = "Rust" // sync me with plugin.xml

    override fun createComponent(): JComponent = layout {
        rustProjectSettings.attachTo(this)
        row("Expand declarative macros (may be slow):", expandMacrosCheckbox, """
            Allow plugin to process declarative macro invocations
            to extract information for name resolution and type inference.
        """)
        val supportedHintOptions = hintProvider.supportedOptions
        if (supportedHintOptions.isNotEmpty()) {
            block("Hints") {
                for (option in supportedHintOptions) {
                    row("${option.name}:", checkboxForOption(option))
                }
            }
        }
    }

    override fun disposeUIResources() = Disposer.dispose(rustProjectSettings)

    override fun reset() {
        val toolchain = settings.toolchain ?: RustToolchain.suggest()

        rustProjectSettings.data = RustProjectSettingsPanel.Data(
            toolchain = toolchain,
            explicitPathToStdlib = settings.explicitPathToStdlib
        )
        expandMacros = settings.expandMacros

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

        val currentData = settings.data
        settings.data = currentData.copy(
            toolchain = rustProjectSettings.data.toolchain,
            explicitPathToStdlib = rustProjectSettings.data.explicitPathToStdlib,
            expandMacros = expandMacros
        )
    }

    override fun isModified(): Boolean {
        val data = rustProjectSettings.data
        if (hintProvider.supportedOptions.any { checkboxForOption(it).isSelected != it.get() }) return true
        return data.toolchain?.location != settings.toolchain?.location
            || data.explicitPathToStdlib != settings.explicitPathToStdlib
            || expandMacros != settings.expandMacros
    }

    private fun checkboxForOption(opt: Option): JBCheckBox = hintCheckboxes[opt.id]!!
}
