package org.rust.cargo.runconfig.forms

import backcompat.ui.components.CheckBox
import backcompat.ui.components.Label
import backcompat.ui.layout.*
import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.RawCommandLineEditor
import org.rust.cargo.runconfig.CargoCommandConfiguration
import javax.swing.JComponent
import javax.swing.JTextField


class CargoRunConfigurationEditorForm : SettingsEditor<CargoCommandConfiguration>() {

    private val comboModules = ModulesComboBox()
    private val command = JTextField()
    private val additionalArguments = RawCommandLineEditor()
    private val environmentVariables = EnvironmentVariablesComponent()
    private val printBacktrace = CheckBox("Print back&trace")

    override fun resetEditorFrom(configuration: CargoCommandConfiguration) {
        command.text = configuration.command

        comboModules.setModules(configuration.validModules)
        comboModules.selectedModule = configuration.configurationModule.module

        additionalArguments.text = configuration.additionalArguments
        environmentVariables.envs = configuration.environmentVariables
        printBacktrace.isSelected = configuration.printBacktrace
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: CargoCommandConfiguration) {
        configuration.command = command.text
        configuration.setModule(comboModules.selectedModule)
        configuration.additionalArguments = additionalArguments.text
        configuration.environmentVariables = environmentVariables.envs
        configuration.printBacktrace = printBacktrace.isSelected
    }

    override fun createEditor(): JComponent = panel {
        labeledRow("Rust project:", comboModules) { comboModules(CCFlags.push) }
        labeledRow("&Command:", command) { command(growPolicy = GrowPolicy.SHORT_TEXT) }
        labeledRow("Additional arguments:", additionalArguments) { additionalArguments() }
        row(environmentVariables.label) { environmentVariables() }
        row { printBacktrace() }
    }

    private fun LayoutBuilder.labeledRow(labelText: String, component: JComponent, init: Row.() -> Unit) {
        val label = Label(labelText)
        label.labelFor = component
        row(label) { init() }
    }
}
