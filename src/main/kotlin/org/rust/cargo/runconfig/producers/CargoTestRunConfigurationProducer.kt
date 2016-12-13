package org.rust.cargo.runconfig.producers

import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.runconfig.CargoCommandConfiguration
import org.rust.cargo.runconfig.CargoCommandRunConfigurationType
import org.rust.cargo.util.cargoProject
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustFnItemElement
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.functions
import org.rust.lang.core.psi.util.module
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.crateRoot

class CargoTestRunConfigurationProducer : RunConfigurationProducer<CargoCommandConfiguration>(CargoCommandRunConfigurationType()) {

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val location = context.location ?: return false
        val test = findTest(location) ?: return false

        return configuration.command == CargoConstants.Commands.TEST &&
            configuration.additionalArguments == test.commandLineParameters
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val location = context.location ?: return false
        val test = findTest(location) ?: return false
        sourceElement.set(test.sourceElement)

        configuration.command = CargoConstants.Commands.TEST
        configuration.name = test.configurationName
        configuration.additionalArguments = test.commandLineParameters
        return true
    }

    private data class TestConfig(
        val sourceElement: RustCompositeElement,
        val configurationName: String,
        val testPath: String,
        val target: CargoProjectDescription.Target
    ) {
        val commandLineParameters: String get() {
            val targetKind = when (target.kind) {
                CargoProjectDescription.TargetKind.BIN -> "bin"
                CargoProjectDescription.TargetKind.TEST -> "test"
                CargoProjectDescription.TargetKind.EXAMPLE -> "example"
                CargoProjectDescription.TargetKind.BENCH -> "bench"
                CargoProjectDescription.TargetKind.LIB -> return "--lib $testPath"
                CargoProjectDescription.TargetKind.UNKNOWN -> return testPath
            }

            return "--$targetKind ${target.name} $testPath"
        }
    }

    private fun findTest(location: Location<*>): TestConfig? = listOfNotNull(
        findTestFunction(location),
        findTestMod(location)
    ).firstOrNull()

    private fun findTestFunction(location: Location<*>): TestConfig? {
        val fn = location.psiElement.parentOfType<RustFnItemElement>() ?: return null
        val name = fn.name ?: return null
        val target = cargoTargetForElement(fn) ?: return null
        return if (fn.isTest) TestConfig(fn, "Test $name", name, target) else null
    }

    private fun findTestMod(location: Location<*>): TestConfig? {
        val mod = location.psiElement.parentOfType<RustMod>(strict = false) ?: return null
        val testName = if (mod.modName == "test" || mod.modName == "tests")
            "Test ${mod.`super`?.modName}::${mod.modName}"
        else
            "Test ${mod.modName}"

        // We need to chop off heading colon `::`, since `crateRelativePath`
        // always returns fully-qualified path
        val testPath = (mod.crateRelativePath ?: "").toString().removePrefix("::")
        val target = cargoTargetForElement(mod) ?: return null
        return if (mod.functions.any { it.isTest }) TestConfig(mod, testName, testPath, target) else null
    }

    private fun cargoTargetForElement(element: RustCompositeElement): CargoProjectDescription.Target? {
        val crateRoot = element.crateRoot ?: return null
        return element.module?.cargoProject?.findTargetForCrateRootFile(crateRoot.containingFile.virtualFile)
    }
}

