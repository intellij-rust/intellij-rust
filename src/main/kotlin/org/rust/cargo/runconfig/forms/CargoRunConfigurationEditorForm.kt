package org.rust.cargo.runconfig.forms

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.RawCommandLineEditor
import org.rust.cargo.runconfig.CargoCommandConfiguration
import javax.swing.JComponent
import javax.swing.JTextField


class CargoRunConfigurationEditorForm : SettingsEditor<CargoCommandConfiguration>() {

    private lateinit var root: JComponent
    private lateinit var command: JTextField
    private lateinit var comboModules: ModulesComboBox
    private lateinit var additionalArguments: RawCommandLineEditor
    private lateinit var environmentVariables: EnvironmentVariablesComponent

    override fun resetEditorFrom(configuration: CargoCommandConfiguration) {
        command.text = configuration.command

        comboModules.fillModules(configuration.project)
        comboModules.selectedModule = configuration.configurationModule.module

        additionalArguments.text = configuration.additionalArguments
        environmentVariables.envs = configuration.environmentVariables
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: CargoCommandConfiguration) {
        configuration.command = command.text
        configuration.setModule(comboModules.selectedModule)
        configuration.additionalArguments = additionalArguments.text
        configuration.environmentVariables = environmentVariables.envs
    }

    override fun createEditor(): JComponent = root
}
