/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.util.PlatformUtils
import org.rust.RsBundle
import org.rust.ide.icons.RsIcons

class CargoCommandConfigurationType : ConfigurationTypeBase(
    "CargoCommandRunConfiguration",
    RsBundle.message("build.event.title.cargo"),
    RsBundle.message("cargo.command.run.configuration"),
    RsIcons.RUST
) {
    init {
        addFactory(CargoConfigurationFactory(this))
    }

    val factory: ConfigurationFactory get() = configurationFactories.single()

    override fun getHelpTopic(): String? =
        // Only IDEA and CLion have special page about IntelliJ Rust in their help
        if (PlatformUtils.isIntelliJ() || PlatformUtils.isCLion()) "rundebugconfigs.cargocommand" else null

    companion object {
        fun getInstance(): CargoCommandConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(CargoCommandConfigurationType::class.java)
    }
}

class CargoConfigurationFactory(type: CargoCommandConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = ID

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return CargoCommandConfiguration(project, "Cargo", this)
    }

    companion object {
        const val ID: String = "Cargo Command"
    }
}
