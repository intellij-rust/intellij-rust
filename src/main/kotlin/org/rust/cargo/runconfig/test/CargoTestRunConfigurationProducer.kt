/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.cargoArgumentSpeck
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.cargo.runconfig.mergeWithDefault
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.containingCargoTarget
import org.rust.lang.core.psi.ext.isTest
import org.rust.lang.core.psi.ext.parentOfType

class CargoTestRunConfigurationProducer : RunConfigurationProducer<CargoCommandConfiguration>(CargoCommandConfigurationType()) {

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val location = context.location ?: return false
        val test = findTest(location) ?: return false

        return configuration.configurationModule.module == context.module &&
            configuration.cargoCommandLine == test.cargoCommandLine
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val location = context.location ?: return false
        val test = findTest(location) ?: return false
        sourceElement.set(test.sourceElement)

        configuration.configurationModule.module = context.module
        configuration.name = test.configurationName
        configuration.cargoCommandLine = test.cargoCommandLine.mergeWithDefault(configuration.cargoCommandLine)
        return true
    }

    private class TestConfig(
        val sourceElement: RsCompositeElement,
        val configurationName: String,
        testPath: String,
        target: CargoWorkspace.Target
    ) {
        val cargoCommandLine: CargoCommandLine = CargoCommandLine(
            CargoConstants.Commands.TEST,
            target.cargoArgumentSpeck + testPath
        )
    }

    private fun findTest(location: Location<*>): TestConfig? =
        findTestFunction(location)
            ?: findTestMod(location)

    private fun findTestFunction(location: Location<*>): TestConfig? {
        val fn = location.psiElement.parentOfType<RsFunction>(strict = false) ?: return null
        val testName = fn.testName() ?: return null
        val name = fn.crateRelativePath.configPath() ?: return null
        val target = fn.containingCargoTarget ?: return null
        return TestConfig(fn, testName, name, target)
    }

    private fun findTestMod(location: Location<*>): TestConfig? {
        val mod = location.psiElement.parentOfType<RsMod>(strict = false) ?: return null
        val testName = mod.testName() ?: return null

        val testPath = mod.crateRelativePath.configPath() ?: ""
        val target = mod.containingCargoTarget ?: return null

        return TestConfig(mod, testName, testPath, target)
    }
}

fun PsiElement.testName(): String? {
    return when (this) {
        is RsMod -> {
            if (!functionList.any { it.isTest }) return null
            if (modName == "test" || modName == "tests")
                "Test ${`super`?.modName}::$modName"
            else
                "Test $modName"
        }
        is RsFunction -> {
            if (!isTest) return null
            val name = crateRelativePath.configPath() ?: return null
            "Test $name"
        }
        else -> null
    }
}


// We need to chop off heading colon `::`, since `crateRelativePath`
// always returns fully-qualified path
private fun String?.configPath(): String? = this?.removePrefix("::")
