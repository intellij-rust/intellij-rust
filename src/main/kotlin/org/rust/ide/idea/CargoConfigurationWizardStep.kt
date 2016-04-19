package org.rust.ide.idea

import com.intellij.ide.util.importProject.ModuleDescriptor
import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import org.rust.cargo.projectSettings.rustSettings
import org.rust.cargo.projectSettings.ui.RustProjectSettingsPanel
import javax.swing.JComponent

class CargoConfigurationWizardStep(
    private val projectDescriptor: ProjectDescriptor
) : ModuleWizardStep() {

    private val cargoSettingsPanel = RustProjectSettingsPanel()
    private val component = cargoSettingsPanel.createComponent()

    override fun getComponent(): JComponent = component

    override fun disposeUIResources() = cargoSettingsPanel.disposeUIResources()

    override fun updateDataModel() {
        val oldDescriptor = projectDescriptor.modules.single()

        val newDescriptor = ModuleDescriptor(oldDescriptor.contentRoots.single(), oldDescriptor.moduleType, emptyList())
        val updater = createConfigurationUpdater(cargoSettingsPanel.data)
        newDescriptor.addConfigurationUpdater(updater)

        projectDescriptor.modules = listOf(newDescriptor)
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
