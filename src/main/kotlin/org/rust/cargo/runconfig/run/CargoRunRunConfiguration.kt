package org.rust.cargo.runconfig.run

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.CargoRunConfigurationBase
import org.rust.cargo.runconfig.CargoRunConfigurationModule
import org.rust.cargo.runconfig.ui.CargoRunRunConfigurationEditForm

/**
 * [RunConfiguration] that runs the main binary of a local package
 */
class CargoRunRunConfiguration(project: Project,
                               name: String,
                               configurationType: CargoRunRunConfigurationType)
: CargoRunConfigurationBase(name,
                            CargoRunConfigurationModule(project),
                            configurationType) {

    override var command: String = "run"

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = CargoRunRunConfigurationEditForm()
}
