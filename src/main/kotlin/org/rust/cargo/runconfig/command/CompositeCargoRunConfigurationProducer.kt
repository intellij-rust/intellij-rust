/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.execution.PsiLocation
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.rust.cargo.runconfig.test.CargoBenchRunConfigurationProducer
import org.rust.cargo.runconfig.test.CargoTestRunConfigurationProducer
import java.util.*
import java.util.function.Function

/**
 * This class aggregates other Rust run configuration [producers] and manages the search & creation of run
 * configurations, taking into account configurations that other [producers] can create.
 * The problem with the previous approach is that if there is an existing configuration that matches the context, the
 * platform does not compare this configuration with those that can be created by other producers, even if these
 * configurations are better matched with the context (see [#1252](https://github.com/intellij-rust/intellij-rust/issues/1252)).
 */
class CompositeCargoRunConfigurationProducer : CargoRunConfigurationProducer() {
    private val producers: List<CargoRunConfigurationProducer> =
        listOf(
            CargoExecutableRunConfigurationProducer(),
            CargoTestRunConfigurationProducer(),
            CargoBenchRunConfigurationProducer()
        )

    override fun findExistingConfiguration(context: ConfigurationContext): RunnerAndConfigurationSettings? {
        val preferredConfig = createPreferredConfigurationFromContext(context) ?: return null
        val runManager = RunManager.getInstance(context.project)
        val configurations = getConfigurationSettingsList(runManager)
        for (configurationSettings in configurations) {
            if (preferredConfig.configuration.isSame(configurationSettings.configuration)) {
                return configurationSettings
            }
        }
        return null
    }

    override fun findOrCreateConfigurationFromContext(context: ConfigurationContext): ConfigurationFromContext? {
        val preferredConfig = createPreferredConfigurationFromContext(context) ?: return null
        val psiElement = preferredConfig.sourceElement
        val locationFromContext = context.location ?: return null
        val locationFromElement = PsiLocation.fromPsiElement(psiElement, locationFromContext.module)
        if (locationFromElement != null) {
            val settings = findExistingConfiguration(context)
            if (preferredConfig.configuration.isSame(settings?.configuration)) {
                preferredConfig.setConfigurationSettings(settings)
            } else {
                // BACKCOMPAT: 2019.1
                @Suppress("DEPRECATION")
                RunManager.getInstance(context.project).setUniqueNameIfNeed(preferredConfig.configuration)
            }
        }
        return preferredConfig
    }

    override fun isConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext
    ): Boolean = producers.any { it.isConfigurationFromContext(configuration, context) }

    override fun setupConfigurationFromContext(
        configuration: CargoCommandConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean = producers.any { it.setupConfigurationFromContext(configuration, context, sourceElement) }

    override fun createLightConfiguration(context: ConfigurationContext): RunConfiguration? {
        val producer = getPreferredProducerForContext(context) ?: return null
        val configuration =
            configurationFactory.createTemplateConfiguration(context.project) as CargoCommandConfiguration
        val ref = Ref(context.psiLocation)
        try {
            if (!producer.setupConfigurationFromContext(configuration, context, ref)) {
                return null
            }
        } catch (e: ClassCastException) {
            return null
        }
        return configuration
    }

    private fun createPreferredConfigurationFromContext(context: ConfigurationContext): ConfigurationFromContext? =
        producers
            .mapNotNull { it.createConfigurationFromContext(context) }
            .sortedWith(ConfigurationFromContext.COMPARATOR)
            .firstOrNull()

    private fun getPreferredProducerForContext(context: ConfigurationContext): CargoRunConfigurationProducer? =
        producers.asSequence()
            .mapNotNull { it.createConfigurationFromContext(context)?.let { key -> Pair(key, it) } }
            .sortedWith(
                Comparator.comparing(
                    Function(Pair<ConfigurationFromContext, *>::first::get),
                    ConfigurationFromContext.COMPARATOR
                )
            )
            .map { it.second }
            .firstOrNull()

    private fun RunConfiguration.isSame(other: RunConfiguration?): Boolean =
        when {
            this === other -> true
            this !is CargoCommandConfiguration || other !is CargoCommandConfiguration -> equals(other)
            channel != other.channel -> false
            command != other.command -> false
            backtrace != other.backtrace -> false
            workingDirectory != other.workingDirectory -> false
            env != other.env -> false
            else -> true
        }
}
