package org.rust.cargo.runconfig.command

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.CargoRunConfigurationBase
import org.rust.cargo.runconfig.CargoRunConfigurationModule
import org.rust.cargo.runconfig.ui.CargoCommandRunConfigurationEditForm

/**
 * [RunConfiguration] that runs a custom cargo command.
 */
class CargoCommandRunConfiguration(project: Project,
                                   name: String,
                                   configurationType: CargoCommandRunConfigurationType)
: CargoRunConfigurationBase (name,
                             CargoRunConfigurationModule(project),
                             configurationType) {

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = CargoCommandRunConfigurationEditForm()
}
