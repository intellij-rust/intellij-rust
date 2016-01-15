package org.rust.cargo.runconfig

import com.intellij.application.options.ModulesComboBox
import com.intellij.openapi.options.SettingsEditor
import org.rust.cargo.project.module.RustExecutableModuleType
import java.awt.Component
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class RustRunConfigurationEditorForm : SettingsEditor<RustApplicationConfiguration>(null) {
    private val rootComponent: JComponent
    private val isRelease: JCheckBox
    private val comoboModules: ModulesComboBox

    init {
        rootComponent = JPanel()
        isRelease = JCheckBox("--release")
        comoboModules = ModulesComboBox()

        listOf<Component>(isRelease, comoboModules).forEach {
            rootComponent.add(it)
        }
    }

    override fun resetEditorFrom(configuration: RustApplicationConfiguration) {
        isRelease.isSelected = configuration.isRelease

        comoboModules.fillModules(configuration.project, RustExecutableModuleType.INSTANCE)
        comoboModules.selectedModule = configuration.configurationModule.module
    }

    override fun applyEditorTo(configuration: RustApplicationConfiguration) {
        configuration.isRelease = isRelease.isSelected
        configuration.setModule(comoboModules.selectedModule)
    }

    override fun createEditor(): JComponent = rootComponent
}
