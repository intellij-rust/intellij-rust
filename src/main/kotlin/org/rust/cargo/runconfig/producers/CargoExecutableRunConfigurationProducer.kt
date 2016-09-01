package org.rust.cargo.runconfig.producers

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.rust.cargo.CargoConstants
import org.rust.cargo.runconfig.CargoCommandConfiguration
import org.rust.cargo.runconfig.CargoCommandRunConfigurationType
import org.rust.cargo.util.cargoProject
import org.rust.lang.core.psi.RustFnElement
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.parentOfType

class CargoExecutableRunConfigurationProducer : RunConfigurationProducer<CargoCommandConfiguration>(CargoCommandRunConfigurationType()) {

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val target = findBinaryTarget(context) ?: return false

        return configuration.command == CargoConstants.Commands.RUN &&
            configuration.additionalArguments == target.additionalArguments
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val target = findBinaryTarget(context) ?: return false
        val fn = context.psiLocation?.parentOfType<RustFnElement>()
        val source = if (fn != null && isMainFunction(fn)) fn else context.psiLocation?.containingFile
        sourceElement.set(source)

        configuration.name = target.configurationName
        configuration.command = CargoConstants.Commands.RUN
        configuration.additionalArguments = target.additionalArguments
        return true
    }

    private class ExecutableTarget(
        name: String,
        kind: String
    ) {
        val configurationName: String = "Run $name"
        val additionalArguments: String = "--$kind $name"
    }

    companion object {
        fun isMainFunction(fn: RustFnElement): Boolean {
            val module = fn.module ?: return false
            return fn.name == "main" && findBinaryTarget(module, fn.containingFile.virtualFile) != null
        }

        private fun findBinaryTarget(context: ConfigurationContext): ExecutableTarget? {
            val module = context.module ?: return null
            val file = context.location?.virtualFile ?: return null
            return findBinaryTarget(module, file)
        }

        private fun findBinaryTarget(module: Module, file: VirtualFile): ExecutableTarget? {
            val target = module.cargoProject?.findTargetForFile(file) ?: return null
            return when {
                target.isBin -> ExecutableTarget(target.name, "bin")
                target.isExample -> ExecutableTarget(target.name, "example")
                else -> null
            }
        }
    }

}
