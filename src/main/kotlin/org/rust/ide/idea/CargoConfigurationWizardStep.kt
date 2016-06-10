package org.rust.ide.idea

import com.intellij.ide.util.importProject.ModuleDescriptor
import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.roots.ModifiableRootModel
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import javax.swing.JComponent

class CargoConfigurationWizardStep(
    private val projectDescriptor: ProjectDescriptor
) : ModuleWizardStep() {

    private lateinit var root: JComponent
    private lateinit var rustProjectSettings: RustProjectSettingsPanel

    override fun getComponent(): JComponent = root

    override fun disposeUIResources() = rustProjectSettings.disposeUIResources()

    override fun updateDataModel() {
        val oldDescriptor = projectDescriptor.modules.single()

        val newDescriptor = ModuleDescriptor(oldDescriptor.contentRoots.single(), oldDescriptor.moduleType, emptyList())
        val updater = createConfigurationUpdater(rustProjectSettings.data)
        newDescriptor.addConfigurationUpdater(updater)

        projectDescriptor.modules = listOf(newDescriptor)
    }

    @Throws(ConfigurationException::class)
    override fun validate(): Boolean {
        rustProjectSettings.validateSettings()
        return true
    }

    companion object {
        private fun createConfigurationUpdater(data: RustProjectSettingsPanel.Data): ModuleConfigurationUpdater =
            object : ModuleConfigurationUpdater() {
                override fun update(module: Module, rootModel: ModifiableRootModel) {
                    val project = module.project
                    val settings = project.rustSettings
                    data.applyTo(settings)
                }
            }
    }

}
