package org.rust.cargo.runconfig.command

import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.parentOfType

class CargoExecutableRunConfigurationProducer : RunConfigurationProducer<CargoCommandConfiguration>(CargoCommandConfigurationType()) {

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val location = context.location ?: return false
        val target = findBinaryTarget(location) ?: return false

        return configuration.configurationModule.module == context.module &&
            configuration.cargoCommandLine == target.cargoArgs
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val location = context.location ?: return false
        val target = findBinaryTarget(location) ?: return false
        val fn = location.psiElement.parentOfType<RsFunction>()
        val source = if (fn != null && isMainFunction(fn)) fn else context.psiLocation?.containingFile
        sourceElement.set(source)

        configuration.configurationModule.module = context.module
        configuration.name = target.configurationName
        configuration.cargoCommandLine = target.cargoArgs
        return true
    }

    private class ExecutableTarget(
        name: String,
        kind: String
    ) {
        val configurationName: String = "Run $name"

        val cargoArgs = CargoCommandLine(
            CargoConstants.Commands.RUN,
            listOf("--$kind", name)
        )
    }

    companion object {
        fun isMainFunction(fn: RsFunction): Boolean {
            val module = fn.module ?: return false
            return fn.name == "main" && findBinaryTarget(module, fn.containingFile.virtualFile) != null
        }

        private fun findBinaryTarget(location: Location<*>): ExecutableTarget? {
            val module = location.module ?: return null
            val file = location.virtualFile ?: return null
            return findBinaryTarget(module, file)
        }

        private fun findBinaryTarget(module: Module, file: VirtualFile): ExecutableTarget? {
            // TODO: specify workspace package here once
            // https://github.com/rust-lang/cargo/issues/3529
            // is fixed
            val target = module.cargoWorkspace?.findTargetForCrateRootFile(file) ?: return null
            return when {
                target.isBin -> ExecutableTarget(target.name, "bin")
                target.isExample -> ExecutableTarget(target.name, "example")
                else -> null
            }
        }
    }
}
