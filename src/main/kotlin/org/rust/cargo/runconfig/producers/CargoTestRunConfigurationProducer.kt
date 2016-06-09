package org.rust.cargo.runconfig.producers

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.rust.cargo.CargoConstants
import org.rust.cargo.runconfig.CargoCommandConfiguration
import org.rust.cargo.runconfig.CargoCommandRunConfigurationType
import org.rust.lang.core.psi.RustFnItemElement
import org.rust.lang.core.psi.impl.mixin.isTest
import org.rust.lang.core.psi.util.parentOfType

class CargoTestRunConfigurationProducer : RunConfigurationProducer<CargoCommandConfiguration>(CargoCommandRunConfigurationType()) {

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean {

        val test = findTestFunction(context) ?: return false

        return configuration.name == configurationName(test) &&
            configuration.command == CargoConstants.Commands.TEST &&
            configuration.additionalArguments == "${test.name}"
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val test = findTestFunction(context) ?: return false

        configuration.name = configurationName(test)
        configuration.command = CargoConstants.Commands.TEST
        configuration.additionalArguments = "${test.name}"
        return true
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean =
        other.isProducedBy(CargoExecutableRunConfigurationProducer::class.java)

    private fun findTestFunction(context: ConfigurationContext): RustFnItemElement? {
        val fn = context.psiLocation?.parentOfType<RustFnItemElement>() ?: return null
        return if (fn.isTest) fn else null
    }

    private fun configurationName(test: RustFnItemElement) = "Test ${test.name}"
}

