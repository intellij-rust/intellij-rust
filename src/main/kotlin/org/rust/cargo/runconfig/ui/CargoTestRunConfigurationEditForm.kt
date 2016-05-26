package org.rust.cargo.runconfig.ui;

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.RawCommandLineEditor
import org.rust.cargo.runconfig.test.CargoTestRunConfiguration
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Form for [CargoTestRunConfiguration] run configuration.
 */
class CargoTestRunConfigurationEditForm : SettingsEditor<CargoTestRunConfiguration>() {

    private lateinit var root: JPanel
    private lateinit var comboModules: ModulesComboBox
    private lateinit var testName: JTextField
    private lateinit var additionalArguments: RawCommandLineEditor
    private lateinit var environmentVariables: EnvironmentVariablesComponent

    override fun resetEditorFrom(configuration: CargoTestRunConfiguration) {
        comboModules.fillModules(configuration.project)
        comboModules.selectedModule = configuration.configurationModule.module
        testName.text = configuration.testName
        additionalArguments.text = configuration.additionalArguments
        environmentVariables.envs = configuration.environmentVariables
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: CargoTestRunConfiguration) {
        configuration.setModule(comboModules.selectedModule)
        configuration.testName = testName.text
        configuration.arguments = "--test " + testName.text
        configuration.additionalArguments = additionalArguments.text
        configuration.environmentVariables = environmentVariables.envs
    }

    override fun createEditor(): JComponent = root
}
