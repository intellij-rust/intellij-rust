package org.rust.cargo.runconfig.test

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.CargoRunConfigurationBase
import org.rust.cargo.runconfig.CargoRunConfigurationModule
import org.rust.cargo.runconfig.ui.CargoTestRunConfigurationEditForm

/**
 * [RunConfiguration] that runs a rust test module
 */
class CargoTestRunConfiguration(project: Project,
                                name: String,
                                configurationType: CargoTestRunConfigurationType)
: CargoRunConfigurationBase(name,
                            CargoRunConfigurationModule(project),
                            configurationType) {

    override var command: String = "test"

    override var arguments: String = ""
        get() {
            if (testName != "") {
                return "--test " + testName;
            }
            return ""
        }

    var testName: String = ""


    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = CargoTestRunConfigurationEditForm()
}
