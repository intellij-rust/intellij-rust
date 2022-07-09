/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.module

import com.intellij.ide.util.projectWizard.ModuleBuilder.ModuleConfigurationUpdater
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.setup
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel
import org.rust.ide.newProject.ui.RsNewProjectPanel
import org.rust.openapiext.isFeatureEnabled
import org.rust.openapiext.pathAsPath
import javax.swing.JComponent

class CargoConfigurationWizardStep(
    private val context: WizardContext,
    private val configurationUpdaterConsumer: ((ModuleConfigurationUpdater) -> Unit)? = null
) : ModuleWizardStep() {

    private val newProjectPanel = RsNewProjectPanel(showProjectTypeSelection = configurationUpdaterConsumer == null)

    override fun getComponent(): JComponent = panel {
        newProjectPanel.attachTo(this)
    }.withBorderIfNeeded()

    override fun disposeUIResources() = Disposer.dispose(newProjectPanel)

    override fun updateDataModel() {
        val data = newProjectPanel.data
        ConfigurationUpdater.data = data.settings

        val projectBuilder = context.projectBuilder
        if (projectBuilder is RsModuleBuilder) {
            projectBuilder.configurationData = data
            projectBuilder.addModuleConfigurationUpdater(ConfigurationUpdater)
        } else {
            configurationUpdaterConsumer?.invoke(ConfigurationUpdater)
        }
    }

    @Throws(ConfigurationException::class)
    override fun validate(): Boolean {
        newProjectPanel.validateSettings()
        return true
    }

    // It's simple hack to imitate new UI style if new project wizard is enabled
    // TODO: drop it and support new project wizard properly
    //  see https://github.com/intellij-rust/intellij-rust/issues/8585
    private fun <T : JComponent> T.withBorderIfNeeded(): T {
        if (isNewWizard()) {
            // border size is taken from `com.intellij.ide.wizard.NewProjectWizardStepPanel`
            border = JBUI.Borders.empty(14, 20)
        }
        return this
    }

    private fun isNewWizard(): Boolean = isFeatureEnabled("new.project.wizard")

    private object ConfigurationUpdater : ModuleConfigurationUpdater() {
        var data: RustProjectSettingsPanel.Data? = null

        override fun update(module: Module, rootModel: ModifiableRootModel) {
            val data = data
            if (data != null) {
                module.project.rustSettings.modify {
                    it.toolchain = data.toolchain
                    it.explicitPathToStdlib = data.explicitPathToStdlib
                }
            }
            // We don't use SDK, but let's inherit one to reduce the amount of
            // "SDK not configured" errors
            // https://github.com/intellij-rust/intellij-rust/issues/1062
            rootModel.inheritSdk()

            val contentEntry = rootModel.contentEntries.singleOrNull()
            if (contentEntry != null) {
                val manifest = contentEntry.file?.findChild(CargoConstants.MANIFEST_FILE)
                if (manifest != null) {
                    module.project.cargoProjects.attachCargoProject(manifest.pathAsPath)
                }

                val projectRoot = contentEntry.file ?: return
                contentEntry.setup(projectRoot)
            }
        }
    }
}
