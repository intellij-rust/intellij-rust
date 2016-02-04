package org.rust.cargo.runconfig.forms

import com.intellij.application.options.ModulesComboBox
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.RawCommandLineEditor
import org.rust.cargo.project.module.RustModuleType
import org.rust.cargo.runconfig.CargoCommandConfiguration

import javax.swing.*


class CargoRunConfigurationEditorForm : SettingsEditor<CargoCommandConfiguration>(null) {

    private lateinit var root: JComponent
    private lateinit var command: JTextField
    private lateinit var comboModules: ModulesComboBox
    private lateinit var additionalArguments: RawCommandLineEditor

    override fun resetEditorFrom(configuration: CargoCommandConfiguration) {
        command.text = configuration.command

        comboModules.fillModules(configuration.project, RustModuleType.INSTANCE)
        comboModules.selectedModule = configuration.configurationModule.module

        additionalArguments.text = configuration.additionalArguments
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: CargoCommandConfiguration) {
        configuration.command = command.text
        configuration.setModule(comboModules.selectedModule)
        configuration.additionalArguments = additionalArguments.text
    }

    override fun createEditor(): JComponent = root
}
