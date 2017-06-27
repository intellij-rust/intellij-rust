/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.idea

import com.intellij.ui.layout.panel
import com.intellij.ide.util.importProject.ProjectDescriptor
import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import javax.swing.JComponent

class CargoConfigurationWizardStep(
    private val context: WizardContext,
    private val projectDescriptor: ProjectDescriptor? = null
) : ModuleWizardStep() {

    private val rustProjectSettings = RustProjectSettingsPanel()

    override fun getComponent(): JComponent = panel {
        rustProjectSettings.attachTo(this)
    }

    override fun disposeUIResources() = Disposer.dispose(rustProjectSettings)

    override fun updateDataModel() {
        ConfigurationUpdater.data = rustProjectSettings.data

        val projectBuilder = context.projectBuilder
        if (projectBuilder is RsModuleBuilder) {
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

    private object ConfigurationUpdater : ModuleConfigurationUpdater() {
        var data: RustProjectSettingsPanel.Data? = null

        override fun update(module: Module, rootModel: ModifiableRootModel) {
            data?.applyTo(module.project.rustSettings)
            // We don't use SDK, but let's inherit one to reduce the amount of
            // "SDK not configured" errors
            // https://github.com/intellij-rust/intellij-rust/issues/1062
            rootModel.inheritSdk()

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
