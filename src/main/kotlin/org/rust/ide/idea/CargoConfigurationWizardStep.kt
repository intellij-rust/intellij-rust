package org.rust.ide.idea

import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import javax.swing.JComponent

class CargoConfigurationWizardStep(
    private val context: WizardContext,
    private val projectDescriptor: ProjectDescriptor? = null
) : ModuleWizardStep(), Disposable {

    private lateinit var root: JComponent
    private lateinit var rustProjectSettings: RustProjectSettingsPanel

    override fun getComponent(): JComponent = root

    override fun disposeUIResources() = rustProjectSettings.disposeUIResources()

    override fun updateDataModel() {
        // XXX: this method may be called several times if user switches back and forth between wizard steps,
        // so we need to make `ConfigurationUpdater` idempotent.
        ConfigurationUpdater.data = rustProjectSettings.data

        val projectBuilder = context.projectBuilder
        if (projectBuilder is RustModuleBuilder) {
            projectBuilder.rustProjectData = rustProjectSettings.data
            projectBuilder.addModuleConfigurationUpdater(ConfigurationUpdater)
        } else {
            projectDescriptor?.modules?.firstOrNull()?.addConfigurationUpdater(ConfigurationUpdater)
        }
    }

    @Throws(ConfigurationException::class)
    override fun validate(): Boolean {
        rustProjectSettings.validateSettings()
        return true
    }

    override fun dispose() {
        rustProjectSettings.disposeUIResources()
    }

    private object ConfigurationUpdater : ModuleConfigurationUpdater() {
        private var alreadyExecuted = false
        var data: RustProjectSettingsPanel.Data? = null

        override fun update(module: Module, rootModel: ModifiableRootModel) {
            if (alreadyExecuted) return
            alreadyExecuted = true

            val latestData = data ?: return
            latestData.applyTo(module.project.rustSettings)

            val contentEntry = rootModel.contentEntries.singleOrNull()
            if (contentEntry != null) {
                val projectRoot = contentEntry.file ?: return
                val makeVfsUrl = { dirName: String -> FileUtil.join(projectRoot.url, dirName) }
                CargoConstants.ProjectLayout.sources.map(makeVfsUrl).forEach {
                    contentEntry.addSourceFolder(it, /* test = */ false)
                }
                CargoConstants.ProjectLayout.tests.map(makeVfsUrl).forEach {
                    contentEntry.addSourceFolder(it, /* test = */ true)
                }
                contentEntry.addExcludeFolder(makeVfsUrl(CargoConstants.ProjectLayout.target))
            }
        }
    }

}
