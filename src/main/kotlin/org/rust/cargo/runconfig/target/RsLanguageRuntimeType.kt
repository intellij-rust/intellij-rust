/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.target

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.RsCommandConfiguration
import org.rust.ide.icons.RsIcons
import java.util.function.Supplier
import javax.swing.Icon

class RsLanguageRuntimeType : LanguageRuntimeType<RsLanguageRuntimeConfiguration>(TYPE_ID) {
    override val displayName: String = "Rust"
    override val icon: Icon = RsIcons.RUST
    override val configurableDescription: String = "Rust Configuration"
    override val launchDescription: String = "Run Rust Command"

    override fun createSerializer(config: RsLanguageRuntimeConfiguration): PersistentStateComponent<*> = config

    override fun createDefaultConfig(): RsLanguageRuntimeConfiguration = RsLanguageRuntimeConfiguration()

    override fun duplicateConfig(config: RsLanguageRuntimeConfiguration): RsLanguageRuntimeConfiguration {
        return duplicatePersistentComponent(this, config)
    }

    override fun createIntrospector(config: RsLanguageRuntimeConfiguration): Introspector<RsLanguageRuntimeConfiguration>? {
        if (config.rustcPath.isNotBlank() && config.rustcVersion.isNotBlank() &&
            config.cargoPath.isNotBlank() && config.cargoVersion.isNotBlank()) return null
        return RsLanguageRuntimeIntrospector(config)
    }

    override fun createConfigurable(
        project: Project,
        config: RsLanguageRuntimeConfiguration,
        targetEnvironmentType: TargetEnvironmentType<*>,
        targetSupplier: Supplier<TargetEnvironmentConfiguration>
    ): Configurable = RsLanguageRuntimeConfigurable(config)

    override fun findLanguageRuntime(target: TargetEnvironmentConfiguration): RsLanguageRuntimeConfiguration? {
        return target.runtimes.findByType()
    }

    override fun isApplicableTo(runConfig: RunnerAndConfigurationSettings): Boolean {
        return runConfig.configuration is RsCommandConfiguration
    }

    companion object {
        const val TYPE_ID: String = "RsLanguageRuntime"
    }
}
