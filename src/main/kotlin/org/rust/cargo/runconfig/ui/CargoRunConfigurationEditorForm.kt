/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.ui

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.Label
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.Row
import com.intellij.ui.layout.panel
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.util.CargoCommandLineEditor
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel


class CargoRunConfigurationEditorForm(project: Project) : SettingsEditor<CargoCommandConfiguration>() {

    private val comboModules = ModulesComboBox()
    private val command = CargoCommandLineEditor(project, { comboModules.selectedModule?.cargoWorkspace })
    private val backtraceMode = ComboBox<BacktraceMode>().apply {
        BacktraceMode.values()
            .sortedBy { it.index }
            .forEach { addItem(it) }
    }
    private val channelLabel = Label("C&hannel:")
    private val channel = ComboBox<RustChannel>().apply {
        RustChannel.values()
            .sortedBy { it.index }
            .forEach { addItem(it) }
    }
    private val environmentVariables = EnvironmentVariablesComponent()
    private val nocapture = CheckBox("Show stdout/stderr in tests", true)


    override fun resetEditorFrom(configuration: CargoCommandConfiguration) {
        comboModules.setModules(configuration.validModules)
        comboModules.selectedModule = configuration.configurationModule.module

        channel.selectedIndex = configuration.channel.index
        command.text = configuration.command
        nocapture.isSelected = configuration.nocapture
        backtraceMode.selectedIndex = configuration.backtrace.index
        environmentVariables.envData = configuration.env
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: CargoCommandConfiguration) {
        val configChannel = RustChannel.fromIndex(channel.selectedIndex)

        configuration.setModule(comboModules.selectedModule)
        configuration.channel = configChannel
        configuration.command = command.text
        configuration.nocapture = nocapture.isSelected
        configuration.backtrace = BacktraceMode.fromIndex(backtraceMode.selectedIndex)
        configuration.env = environmentVariables.envData

        val rustupAvailable = comboModules.selectedModule?.project?.toolchain?.isRustupAvailable ?: false
        channel.isEnabled = rustupAvailable || configChannel != RustChannel.DEFAULT
        if (!rustupAvailable && configChannel != RustChannel.DEFAULT) {
            throw ConfigurationException("Channel cannot be set explicitly because rustup is not available")
        }
    }

    override fun createEditor(): JComponent = panel {
        labeledRow("Rust &project:", comboModules) { comboModules(CCFlags.push) }
        labeledRow("&Command:", command) {
            command(CCFlags.pushX, CCFlags.growX)
            channelLabel.labelFor = channel
            channelLabel()
            channel()
        }

        row { nocapture() }

        row(environmentVariables.label) { environmentVariables.apply { makeWide() }() }
        labeledRow("Back&trace:", backtraceMode) { backtraceMode() }
    }

    private fun LayoutBuilder.labeledRow(labelText: String, component: JComponent, init: Row.() -> Unit) {
        val label = Label(labelText)
        label.labelFor = component
        row(label) { init() }
    }

    private fun JPanel.makeWide() {
        preferredSize = Dimension(1000, height)
    }
}
