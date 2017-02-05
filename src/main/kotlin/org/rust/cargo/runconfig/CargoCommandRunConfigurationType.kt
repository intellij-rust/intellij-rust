package org.rust.cargo.runconfig

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.rust.ide.icons.RsIcons

class CargoCommandRunConfigurationType : ConfigurationTypeBase("CargoCommandRunConfiguration",
    "Cargo Command",
    "Cargo command run configuration",
    RsIcons.RUST) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                CargoCommandConfiguration(project, "Cargo", this@CargoCommandRunConfigurationType)

            override fun configureBeforeRunTaskDefaults(providerID: Key<out BeforeRunTask<BeforeRunTask<*>>>,
                                                        task: BeforeRunTask<out BeforeRunTask<*>>) {
                if (providerID == CompileStepBeforeRun.ID) {
                    // We don't use jps, so we don't need to execute `Make` task
                    // before run configuration is executed
                    task.isEnabled = false
                }
            }

            override fun isConfigurationSingletonByDefault(): Boolean = true
        })
    }
}
