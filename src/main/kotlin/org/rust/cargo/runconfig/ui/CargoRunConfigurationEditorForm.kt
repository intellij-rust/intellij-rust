package org.rust.cargo.runconfig.ui

import backcompat.ui.components.CheckBox
import backcompat.ui.components.Label
import backcompat.ui.layout.*
import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.RawCommandLineEditor
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.CargoCommandLine
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField


class CargoRunConfigurationEditorForm : SettingsEditor<CargoCommandConfiguration>() {

    private val comboModules = ModulesComboBox()
    private val command = JTextField()
    private val additionalArguments = RawCommandLineEditor()
    private val environmentVariables = EnvironmentVariablesComponent()
    private val printBacktrace = CheckBox("Print back&trace")

    override fun resetEditorFrom(configuration: CargoCommandConfiguration) {
        comboModules.setModules(configuration.validModules)
        comboModules.selectedModule = configuration.configurationModule.module

        configuration.cargoCommandLine.let { args ->
            command.text = args.command
            additionalArguments.text = ParametersListUtil.join(args.additionalArguments)
            printBacktrace.isSelected = args.printBacktrace
            environmentVariables.envs = args.environmentVariables
        }
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: CargoCommandConfiguration) {
        configuration.setModule(comboModules.selectedModule)
        configuration.cargoCommandLine = CargoCommandLine(
            command.text,
            ParametersListUtil.parse(additionalArguments.text),
            printBacktrace.isSelected,
            environmentVariables.envs
        )
    }

    override fun createEditor(): JComponent = panel {
        labeledRow("Rust &project:", comboModules) { comboModules(CCFlags.push) }
        labeledRow("&Command:", command) { command(growPolicy = GrowPolicy.SHORT_TEXT) }
        labeledRow("&Additional arguments:", additionalArguments) { additionalArguments.apply {
            dialogCaption = "Additional arguments"
            makeWide()
        }() }
        row(environmentVariables.label) { environmentVariables.apply { makeWide() }() }
        row { printBacktrace() }
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
