/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.idea

import com.intellij.execution.ExecutionException
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Disposer
import org.rust.cargo.toolchain.RustToolchain
import org.rust.ide.newProject.ConfigurationData
import org.rust.ide.newProject.makeDefaultRunConfiguration
import org.rust.ide.newProject.makeProject
import org.rust.ide.newProject.openFiles

/**
 * Builder which is used when a new project or module is created and not imported from source.
 */
class RsModuleBuilder : ModuleBuilder() {

    override fun getModuleType(): ModuleType<*>? = RsModuleType.INSTANCE

    override fun isSuitableSdkType(sdkType: SdkTypeId?): Boolean = true

    override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep =
        CargoConfigurationWizardStep.newProject(context).apply {
            Disposer.register(parentDisposable, Disposable { this.disposeUIResources() })
        }

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        val root = doAddContentEntry(modifiableRootModel)?.file ?: return
        modifiableRootModel.inheritSdk()
        val toolchain = configurationData?.settings?.toolchain
        root.refresh(/* async = */ false, /* recursive = */ true)

        // Just work if user "creates new project" over an existing one.
        if (toolchain != null && root.findChild(RustToolchain.CARGO_TOML) == null) {
            try {
                // TODO: rewrite this somehow to fix `Synchronous execution on EDT` exception
                // The problem is that `setupRootModel` is called on EDT under write action
                // so `$ cargo init` invocation blocks UI thread

                val template = configurationData?.template ?: return
                val cargo = toolchain.rawCargo()
                val project = modifiableRootModel.project
                val name = project.name.replace(' ', '_')

                val generatedFiles = cargo.makeProject(
                    project,
                    modifiableRootModel.module,
                    root,
                    name,
                    template
                ) ?: return

                project.makeDefaultRunConfiguration(template)
                project.openFiles(generatedFiles)
            } catch (e: ExecutionException) {
                LOG.error(e)
                throw ConfigurationException(e.message)
            }
        }
    }

    @Throws(ConfigurationException::class)
    override fun validateModuleName(moduleName: String): Boolean {
        val errorMessage = configurationData?.template?.validateProjectName(moduleName) ?: return true
        throw ConfigurationException(errorMessage)
    }

    var configurationData: ConfigurationData? = null

    companion object {
        private val LOG = Logger.getInstance(RsModuleBuilder::class.java)
    }
}
