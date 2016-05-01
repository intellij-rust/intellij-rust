package org.rust.cargo.runconfig.run

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.rust.ide.icons.RustIcons

class CargoRunType : ConfigurationTypeBase("CargoRunConfiguration",
                                           "Cargo Run",
                                           "Cargo command run configuration",
                                           RustIcons.RUST) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                CargoRun(project, "Cargo", this@CargoRunType)

            override fun configureBeforeRunTaskDefaults(providerID: Key<out BeforeRunTask<BeforeRunTask<*>>>,
                                                        task: BeforeRunTask<out BeforeRunTask<*>>) {
                if (providerID == CompileStepBeforeRun.ID) {
                    task.isEnabled = false
                }
            }
        })
    }
}
