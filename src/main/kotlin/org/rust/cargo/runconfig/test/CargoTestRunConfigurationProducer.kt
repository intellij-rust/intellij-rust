/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.cargo.runconfig.mergeWithDefault
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.*

class CargoTestRunConfigurationProducer : RunConfigurationProducer<CargoCommandConfiguration>(CargoCommandConfigurationType()) {

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val test = findTest(context) ?: return false
        return configuration.canBeFrom(test.cargoCommandLine())
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val test = findTest(context) ?: return false
        sourceElement.set(test.sourceElement)
        configuration.name = test.configurationName
        val cmd = test.cargoCommandLine().mergeWithDefault(configuration)
        configuration.setFromCmd(cmd)
        return true
    }

    companion object {
        fun findTest(context: ConfigurationContext): TestConfig? {
            val elements: Array<PsiElement>? = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context.dataContext)
                ?: context.location?.psiElement?.let { arrayOf(it) }
            return elements?.let { findTest(it) }
        }

        fun findTest(psi: Array<PsiElement>, climbUp: Boolean = true): TestConfig? {
            return if (psi.isNotEmpty()) {
                var config: TestConfig? = TestConfig.MultipleFileTestConfig.create(psi.mapNotNull {
                    findElement<RsMod>(it, climbUp)
                }.toTypedArray())
                if (config != null) {
                    return config
                }

                config = findElement<RsFunction>(psi[0], climbUp)?.let {
                    TestConfig.SingleTestConfig.create(it)
                }
                if (config != null) {
                    return config
                }

                findElement<RsMod>(psi[0], climbUp)?.let {
                    TestConfig.SingleTestConfig.create(it)
                }
            } else {
                return null
            }
        }

        private inline fun <reified T : PsiElement> findElement(base: PsiElement, climbUp: Boolean): T? {
            if (base is T) return base
            if (!climbUp) return null
            return base.ancestorOrSelf()
        }
    }
}

sealed class TestConfig {
    class SingleTestConfig(
        override val path: String,
        val target: CargoWorkspace.Target,
        override val sourceElement: RsElement
    ) : TestConfig() {
        override val exact = sourceElement is RsFunction

        override val configurationName: String = if (sourceElement is RsMod) {
            if (sourceElement.modName == "test" || sourceElement.modName == "tests")
                "Test ${sourceElement.`super`?.modName}::${sourceElement.modName}"
            else
                "Test ${sourceElement.modName}"
        } else {
            "Test $path"
        }

        override val targets: List<CargoWorkspace.Target>
            get() = listOf(target)

        companion object {
            fun create(element: RsQualifiedNamedElement): TestConfig? {
                val valid = when (element) {
                    is RsMod -> hasTestFunction(element)
                    is RsFunction -> element.isTest
                    else -> throw Exception("expected RsMod or RsFunction")
                }
                if (!valid) return null

                val configPath = element.crateRelativePath.configPath() ?: return null
                val target = element.containingCargoTarget ?: return null
                return SingleTestConfig(configPath, target, element)
            }
        }
    }

    class MultipleFileTestConfig(
        override val targets: List<CargoWorkspace.Target>,
        override val sourceElement: RsElement
    ) : TestConfig() {
        override val configurationName: String = "Test multiple selected files"
        override val exact = false
        override val path: String = ""

        companion object {
            fun create(modules: Array<RsMod>): MultipleFileTestConfig? {
                val modulesWithTests = modules
                    .filter { hasTestFunction(it) && it.containingCargoTarget != null }

                val targets = modulesWithTests
                    .mapNotNull { it.containingCargoTarget }
                if (targets.size <= 1) {
                    return null
                }

                // If the selection spans more than one package, bail out.
                val pkgs = targets.map { it.pkg }.distinct()
                if (pkgs.size > 1) {
                    return null
                }

                return MultipleFileTestConfig(targets, modulesWithTests[0])
            }
        }
    }

    abstract val path: String
    abstract val exact: Boolean
    abstract val targets: List<CargoWorkspace.Target>
    abstract val configurationName: String
    abstract val sourceElement: RsElement

    fun cargoCommandLine(): CargoCommandLine {
        var commandLine = CargoCommandLine.forTargets(targets, "test", listOf(path))
        if (exact) {
            commandLine = commandLine.withDoubleDashFlag("--exact")
        }
        return commandLine
    }

    companion object {
        private fun hasTestFunction(mod: RsMod): Boolean =
            mod.processExpandedItems { it is RsFunction && it.isTest }
    }
}

// We need to chop off heading colon `::`, since `crateRelativePath`
// always returns fully-qualified path
private fun String?.configPath(): String? = this?.removePrefix("::")
