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
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.canonicalCratePath
import org.rust.lang.core.psi.impl.mixin.isTest
import org.rust.lang.core.psi.util.parentOfType

class CargoTestRunConfigurationProducer : RunConfigurationProducer<CargoCommandConfiguration>(CargoCommandRunConfigurationType()) {

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val test = findTest(context) ?: return false

        return configuration.command == CargoConstants.Commands.TEST &&
            configuration.name == test.configurationName &&
            configuration.additionalArguments == test.testPath
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val test = findTest(context) ?: return false

        configuration.command = CargoConstants.Commands.TEST
        configuration.name = test.configurationName
        configuration.additionalArguments = test.testPath
        return true
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean =
        other.isProducedBy(CargoExecutableRunConfigurationProducer::class.java)

    private data class TestConfig(
        val configurationName: String,
        val testPath: String
    )

    private fun findTest(context: ConfigurationContext): TestConfig? = listOfNotNull(
        findTestFunction(context),
        findTestMod(context)
    ).firstOrNull()

    private fun findTestFunction(context: ConfigurationContext): TestConfig? {
        val fn = context.psiLocation?.parentOfType<RustFnItemElement>() ?: return null
        val name = fn.name ?: return null
        return if (fn.isTest) TestConfig("Test $name", name) else null
    }

    private fun findTestMod(context: ConfigurationContext): TestConfig? {
        val mod = context.psiLocation?.parentOfType<RustMod>() ?: return null
        val testName = if (mod.modName == "test" || mod.modName == "tests")
            "Test ${mod.`super`?.modName}::${mod.modName}"
        else
            "Test ${mod.modName}"

        val testPath = mod.canonicalCratePath ?: return null
        val functions = mod.itemList.filterIsInstance<RustFnItemElement>()
        return if (functions.any { it.isTest }) TestConfig(testName, testPath) else null
    }
}

