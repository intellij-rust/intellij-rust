package org.rust.cargo.runconfig.producers

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.runconfig.CargoCommandConfiguration
import org.rust.cargo.runconfig.CargoCommandRunConfigurationType
import org.rust.cargo.util.cargoProject

class CargoExecutableRunConfigurationProducer : RunConfigurationProducer<CargoCommandConfiguration>(CargoCommandRunConfigurationType()) {

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val target = findBinaryTarget(context) ?: return false

        return configuration.name == configurationName(target) &&
            configuration.command == CargoConstants.Commands.RUN &&
            configuration.additionalArguments == "--bin ${target.name}"
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val target = findBinaryTarget(context) ?: return false

        configuration.name = configurationName(target)
        configuration.command = CargoConstants.Commands.RUN
        configuration.additionalArguments = "--bin ${target.name}"
        return true
    }

    private fun findBinaryTarget(context: ConfigurationContext): CargoProjectDescription.Target? {
        val file = context.location?.virtualFile ?: return null
        val target = context.module.cargoProject?.findTargetForFile(file) ?: return null
        if (!target.isBin) return null
        return target
    }

    private fun configurationName(target: CargoProjectDescription.Target) = "Run ${target.name}"
}
