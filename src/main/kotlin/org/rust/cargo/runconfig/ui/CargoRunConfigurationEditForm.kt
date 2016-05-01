package org.rust.cargo.runconfig.ui;

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.RawCommandLineEditor
import org.rust.cargo.runconfig.run.CargoRun
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Form for [CargoRun] run configuration.
 */
class CargoRunConfigurationEditForm : SettingsEditor<CargoRun>() {

    private lateinit var root: JPanel
    private lateinit var comboModules: ModulesComboBox
    private lateinit var additionalArguments: RawCommandLineEditor
    private lateinit var environmentVariables: EnvironmentVariablesComponent

    override fun resetEditorFrom(configuration: CargoRun) {
        comboModules.fillModules(configuration.project)
        comboModules.selectedModule = configuration.configurationModule.module

        additionalArguments.text = configuration.additionalArguments
        environmentVariables.envs = configuration.environmentVariables
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: CargoRun) {
        configuration.setModule(comboModules.selectedModule)
        configuration.additionalArguments = additionalArguments.text
        configuration.environmentVariables = environmentVariables.envs
    }

    override fun createEditor(): JComponent = root
}
