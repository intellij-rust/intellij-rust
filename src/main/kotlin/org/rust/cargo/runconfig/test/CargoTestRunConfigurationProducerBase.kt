/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoRunConfigurationProducer
import org.rust.cargo.runconfig.mergeWithDefault
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.*

abstract class CargoTestRunConfigurationProducerBase : CargoRunConfigurationProducer() {
    abstract val commandName: String

    abstract fun isSuitable(element: RsElement): Boolean

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val existingConfig = findConfig(context) ?: return false
        return configuration.canBeFrom(existingConfig.cargoCommandLine())
    }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val existingConfig = findConfig(context) ?: return false
        sourceElement.set(existingConfig.sourceElement)
        configuration.name = existingConfig.configurationName
        val cmd = existingConfig.cargoCommandLine().mergeWithDefault(configuration)
        configuration.setFromCmd(cmd)
        return true
    }

    fun findConfig(psi: Array<PsiElement>, climbUp: Boolean = true): TestConfig? {
        if (psi.isEmpty()) return null
        createMultipleFileConfiguration(psi.mapNotNull { findElement<RsMod>(it, climbUp) }.toTypedArray())
            ?.let { return it }
        findElement<RsFunction>(psi[0], climbUp)
            ?.let { createSingleConfiguration(it) }
            ?.let { return it }
        return findElement<RsMod>(psi[0], climbUp)
            ?.let { createSingleConfiguration(it) }
    }

    private fun findConfig(context: ConfigurationContext): TestConfig? {
        val elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context.dataContext)
            ?: context.location?.psiElement?.let { arrayOf(it) }
        return elements?.let { findConfig(it) }
    }

    private fun createSingleConfiguration(element: RsQualifiedNamedElement): TestConfig? {
        if (!isSuitable(element)) return null
        val configPath = element.crateRelativePath.configPath() ?: return null
        val target = element.containingCargoTarget ?: return null
        return TestConfig.SingleTestConfig(commandName, configPath, target, element)
    }

    private fun createMultipleFileConfiguration(modules: Array<RsMod>): TestConfig? {
        val modulesWithBenches = modules.filter { isSuitable(it) && it.containingCargoTarget != null }

        val targets = modulesWithBenches.mapNotNull { it.containingCargoTarget }
        if (targets.size <= 1) return null

        // If the selection spans more than one package, bail out.
        val pkgs = targets.map { it.pkg }.distinct()
        if (pkgs.size > 1) return null

        return TestConfig.MultipleFileTestConfig(commandName, targets, modulesWithBenches[0])
    }

    companion object {
        // We need to chop off heading colon `::`, since `crateRelativePath` always returns fully-qualified path
        @JvmStatic
        protected fun String?.configPath(): String? = this?.removePrefix("::")

        private inline fun <reified T : PsiElement> findElement(base: PsiElement, climbUp: Boolean): T? {
            if (base is T) return base
            if (!climbUp) return null
            return base.ancestorOrSelf()
        }
    }
}

sealed class TestConfig {
    abstract val commandName: String
    abstract val path: String
    abstract val exact: Boolean
    abstract val targets: List<CargoWorkspace.Target>
    abstract val configurationName: String
    abstract val sourceElement: RsElement

    fun cargoCommandLine(): CargoCommandLine {
        var commandLine = CargoCommandLine.forTargets(targets, commandName, listOf(path))
        if (exact) {
            commandLine = commandLine.withPositionalArgument("--exact")
        }
        return commandLine
    }

    class SingleTestConfig(
        override val commandName: String,
        override val path: String,
        val target: CargoWorkspace.Target,
        override val sourceElement: RsElement
    ) : TestConfig() {
        override val exact: Boolean
            get() = sourceElement is RsFunction

        override val targets: List<CargoWorkspace.Target>
            get() = listOf(target)

        override val configurationName: String
            get() = buildString {
                append(commandName.capitalize())
                append(" ")

                if (sourceElement !is RsMod) {
                    append(path)
                    return@buildString
                }

                if (sourceElement.modName == "test" || sourceElement.modName == "tests") {
                    append(sourceElement.`super`?.modName ?: "")
                    append("::")
                }
                append(sourceElement.modName)
            }
    }

    class MultipleFileTestConfig(
        override val commandName: String,
        override val targets: List<CargoWorkspace.Target>,
        override val sourceElement: RsElement
    ) : TestConfig() {
        override val path: String = ""
        override val exact: Boolean = false

        override val configurationName: String
            get() = "${commandName.capitalize()} multiple selected files"
    }
}
