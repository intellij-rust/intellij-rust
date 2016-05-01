package org.rust.cargo.runconfig.command

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationModule
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.CargoRunConfigurationBase
import org.rust.cargo.runconfig.ui.CargoCommandConfigurationEditForm

class CargoCommand(project: Project,
                   name: String,
                   configurationType: CargoCommandType)
: CargoRunConfigurationBase<CargoCommandConfigurationModule> (name,
                                                              CargoCommandConfigurationModule(project),
                                                              configurationType) {

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = CargoCommandConfigurationEditForm()
}

class CargoCommandConfigurationModule(project: Project) : RunConfigurationModule(project)
