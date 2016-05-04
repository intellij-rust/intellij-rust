package org.rust.cargo.runconfig.command

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.CargoConfigurationFactory
import org.rust.ide.icons.RustIcons

class CargoCommandRunConfigurationType : ConfigurationTypeBase("CargoCommandConfiguration",
                                                               "Cargo Command",
                                                               "Cargo command run configuration",
                                                               RustIcons.RUST) {
    init {
        addFactory(object : CargoConfigurationFactory(this) {
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                CargoCommandRunConfiguration(project, "Cargo", this@CargoCommandRunConfigurationType)
        })
    }
}
