package org.rust.cargo.runconfig.producers

import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.workspace.cargoProject
import org.rust.cargo.runconfig.CargoCommandConfiguration
import org.rust.cargo.runconfig.CargoCommandRunConfigurationType
import org.rust.lang.core.psi.RustFunctionElement
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.parentOfType

class CargoExecutableRunConfigurationProducer : RunConfigurationProducer<CargoCommandConfiguration>(CargoCommandRunConfigurationType()) {

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val location = context.location ?: return false
        val target = findBinaryTarget(location) ?: return false

        return configuration.command == CargoConstants.Commands.RUN &&
            configuration.additionalArguments == target.additionalArguments
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val location = context.location ?: return false
        val target = findBinaryTarget(location) ?: return false
        val fn = location.psiElement.parentOfType<RustFunctionElement>()
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
        fun isMainFunction(fn: RustFunctionElement): Boolean {
            val module = fn.module ?: return false
            return fn.name == "main" && findBinaryTarget(module, fn.containingFile.virtualFile) != null
        }

        private fun findBinaryTarget(location: Location<*>): ExecutableTarget? {
            val module = location.module ?: return null
            val file = location.virtualFile ?: return null
            return findBinaryTarget(module, file)
        }

        private fun findBinaryTarget(module: Module, file: VirtualFile): ExecutableTarget? {
            val target = module.cargoProject?.findTargetForCrateRootFile(file) ?: return null
            return when {
                target.isBin -> ExecutableTarget(target.name, "bin")
                target.isExample -> ExecutableTarget(target.name, "example")
                else -> null
            }
        }
    }

}
