package org.rust.cargo.runconfig.producers

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.runconfig.CargoCommandConfiguration
import org.rust.cargo.runconfig.CargoCommandRunConfigurationType
import org.rust.cargo.util.cargoProject
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.symbols.stringize
import org.rust.lang.core.resolve.util.RustResolveUtil

class CargoTestRunConfigurationProducer : RunConfigurationProducer<CargoCommandConfiguration>(CargoCommandRunConfigurationType()) {

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val test = findTest(context) ?: return false

        return configuration.command == CargoConstants.Commands.TEST &&
            configuration.name == test.configurationName &&
            configuration.additionalArguments == test.commandLineParameters
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val test = findTest(context) ?: return false

        configuration.command = CargoConstants.Commands.TEST
        configuration.name = test.configurationName
        configuration.additionalArguments = test.commandLineParameters
        return true
    }

    override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean =
        other.isProducedBy(CargoExecutableRunConfigurationProducer::class.java)

    private data class TestConfig(
        val configurationName: String,
        val testPath: String,
        val target: CargoProjectDescription.Target
    ) {
        val commandLineParameters: String get() {
            val targetKind = when (target.kind) {
                CargoProjectDescription.TargetKind.BIN     -> "bin"
                CargoProjectDescription.TargetKind.TEST    -> "test"
                CargoProjectDescription.TargetKind.EXAMPLE -> "example"
                CargoProjectDescription.TargetKind.BENCH   -> "bench"
                CargoProjectDescription.TargetKind.LIB     -> return "--lib $testPath"
                CargoProjectDescription.TargetKind.UNKNOWN -> return testPath
            }

            return "--$targetKind ${target.name} $testPath"
        }
    }

    private fun findTest(context: ConfigurationContext): TestConfig? = listOfNotNull(
        findTestFunction(context),
        findTestMod(context)
    ).firstOrNull()

    private fun findTestFunction(context: ConfigurationContext): TestConfig? {
        val fn = context.psiLocation?.parentOfType<RustFnItemElement>() ?: return null
        val name = fn.name ?: return null
        val target = cargoTargetForElement(fn) ?: return null
        return if (fn.isTest) TestConfig("Test $name", name, target) else null
    }

    private fun findTestMod(context: ConfigurationContext): TestConfig? {
        val mod = context.psiLocation?.parentOfType<RustMod>() ?: return null
        val testName = if (mod.modName == "test" || mod.modName == "tests")
            "Test ${mod.`super`?.modName}::${mod.modName}"
        else
            "Test ${mod.modName}"

        val testPath = mod.canonicalCratePath?.stringize() ?: return null
        val target = cargoTargetForElement(mod) ?: return null
        return if (mod.functions.any { it.isTest }) TestConfig(testName, testPath, target) else null
    }

    private fun cargoTargetForElement(element: RustCompositeElement): CargoProjectDescription.Target? {
        val rootMod = RustResolveUtil.getCrateRootModFor(element) ?: return null
        return element.module?.cargoProject?.findTargetForFile(rootMod.containingFile.virtualFile)
    }
}

