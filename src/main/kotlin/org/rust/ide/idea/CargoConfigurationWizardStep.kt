package org.rust.ide.idea

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
        // XXX: this method may be called several times if user switches back and forth between wizard steps,
        // so we need to make `ConfigurationUpdater` idempotent.
        ConfigurationUpdater.data = rustProjectSettings.data
        projectDescriptor.modules.firstOrNull()?.addConfigurationUpdater(ConfigurationUpdater)
    }

    @Throws(ConfigurationException::class)
    override fun validate(): Boolean {
        rustProjectSettings.validateSettings()
        return true
    }

    private object ConfigurationUpdater : ModuleConfigurationUpdater() {
        var data: RustProjectSettingsPanel.Data? = null

        override fun update(module: Module, rootModel: ModifiableRootModel) {
            val latestData = data ?: return
            val project = module.project
            val settings = project.rustSettings
            latestData.applyTo(settings)
            data = null
        }
    }

}
