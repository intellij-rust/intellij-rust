package org.rust.cargo.runconfig

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.openapi.util.Key

abstract class CargoConfigurationFactory(configurationType: ConfigurationTypeBase)
: ConfigurationFactory(configurationType) {

    override fun configureBeforeRunTaskDefaults(providerID: Key<out BeforeRunTask<BeforeRunTask<*>>>,
                                                task: BeforeRunTask<out BeforeRunTask<*>>) {
        if (providerID == CompileStepBeforeRun.ID) {
            // We don't use jps, so we don't need to execute `Make` task
            // before run configuration is executed
            task.isEnabled = false
        }
    }
}
