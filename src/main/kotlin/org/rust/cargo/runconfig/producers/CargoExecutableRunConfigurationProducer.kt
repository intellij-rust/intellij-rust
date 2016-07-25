package org.rust.cargo.runconfig.producers

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.rust.cargo.CargoConstants
import org.rust.cargo.runconfig.CargoCommandConfiguration
import org.rust.cargo.runconfig.CargoCommandRunConfigurationType
import org.rust.cargo.util.cargoProject

class CargoExecutableRunConfigurationProducer : RunConfigurationProducer<CargoCommandConfiguration>(CargoCommandRunConfigurationType()) {

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val target = findBinaryTarget(context) ?: return false

        return configuration.name == target.configurationName &&
            configuration.command == CargoConstants.Commands.RUN &&
            configuration.additionalArguments == target.additionalArguments
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val target = findBinaryTarget(context) ?: return false

        configuration.name = target.configurationName
        configuration.command = CargoConstants.Commands.RUN
        configuration.additionalArguments = target.additionalArguments
        return true
    }

    private fun findBinaryTarget(context: ConfigurationContext): ExecutableTarget? {
        val file = context.location?.virtualFile ?: return null
        val target = context.module?.cargoProject?.findTargetForFile(file) ?: return null
        return when {
            target.isBin -> ExecutableTarget(target.name, "bin")
            target.isExample -> ExecutableTarget(target.name, "example")
            else -> null
        }
    }

    private class ExecutableTarget(
        private val name: String,
        private val kind: String
    ) {
        val configurationName: String = "Run $name"
        val additionalArguments: String = "--$kind $name"
    }

}
