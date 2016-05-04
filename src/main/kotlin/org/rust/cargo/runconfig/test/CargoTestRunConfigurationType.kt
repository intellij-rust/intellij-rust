package org.rust.cargo.runconfig.test

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.CargoConfigurationFactory
import org.rust.ide.icons.RustIcons

class CargoTestRunConfigurationType : ConfigurationTypeBase("CargoTestConfiguration",
                                                            "Cargo Test",
                                                            "Cargo command test configuration",
                                                            RustIcons.RUST) {
    init {
        addFactory(object : CargoConfigurationFactory(this) {
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                CargoTestRunConfiguration(project, "Cargo", this@CargoTestRunConfigurationType)
        })
    }

}
