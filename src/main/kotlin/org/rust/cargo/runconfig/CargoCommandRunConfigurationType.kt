package org.rust.cargo.runconfig

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import org.rust.ide.icons.RustIcons

class CargoCommandRunConfigurationType : ConfigurationTypeBase("CargoCommandRunConfiguration",
                                                               "Cargo Command",
                                                               "Cargo command run configuration",
                                                               RustIcons.RUST) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                CargoCommandConfiguration(project, "Cargo", this@CargoCommandRunConfigurationType)
        })
    }
}
