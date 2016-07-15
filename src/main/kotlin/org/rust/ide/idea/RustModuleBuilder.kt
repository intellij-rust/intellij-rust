package org.rust.ide.idea

import com.intellij.ide.util.projectWizard.JavaModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.util.Pair

/**
 * Builder which is used when a new project or module is created and not imported from source.
 *
 * We inherit from [JavaModuleBuilder] here to properly set up content roots.
 */
class RustModuleBuilder : JavaModuleBuilder() {

    override fun getModuleType(): ModuleType<*>? = RustModuleType.INSTANCE

    override fun getSourcePaths(): List<Pair<String, String>> = emptyList()

    override fun isSuitableSdkType(sdkType: SdkTypeId?): Boolean = true

    override fun createWizardSteps(wizardContext: WizardContext, modulesProvider: ModulesProvider): Array<out ModuleWizardStep> = ModuleWizardStep.EMPTY_ARRAY
}
