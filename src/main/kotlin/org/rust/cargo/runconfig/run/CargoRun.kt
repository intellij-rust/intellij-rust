package org.rust.cargo.runconfig.run

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationModule
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.CargoRunConfigurationBase
import org.rust.cargo.runconfig.ui.CargoRunConfigurationEditForm

/**
 * [RunConfiguration] implementation that runs the main binary of a local package
 */
class CargoRun(project: Project,
               name: String,
               configurationType: CargoRunType)
: CargoRunConfigurationBase<CargoRunConfigurationModule>(name,
                                                         CargoRunConfigurationModule(project),
                                                         configurationType) {

    override var command: String = "run"

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = CargoRunConfigurationEditForm()
}

class CargoRunConfigurationModule(project: Project) : RunConfigurationModule(project)
