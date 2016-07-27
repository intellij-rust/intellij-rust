package org.rust.ide.idea

import com.intellij.execution.ExecutionException
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Disposer
import org.rust.cargo.project.settings.ui.RustProjectSettingsPanel

/**
 * Builder which is used when a new project or module is created and not imported from source.
 */
class RustModuleBuilder : ModuleBuilder() {

    override fun getModuleType(): ModuleType<*>? = RustModuleType.INSTANCE

    override fun isSuitableSdkType(sdkType: SdkTypeId?): Boolean = true

    override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep =
        CargoConfigurationWizardStep(context).apply {
            Disposer.register(parentDisposable, this)
        }

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        doAddContentEntry(modifiableRootModel)
        val toolchain = rustProjectData?.toolchain
        val contentEntryPath = contentEntryPath
        if (toolchain != null && contentEntryPath != null) {
            try {
                toolchain.nonProjectCargo().init(contentEntryPath)
            } catch (e: ExecutionException) {
                throw ConfigurationException("Failed to execute `cargo init`")
            }
        }
    }

    var rustProjectData: RustProjectSettingsPanel.Data? = null
}
