package org.rust.cargo.runconfig.test

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationModule
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.CargoRunConfigurationBase
import org.rust.cargo.runconfig.ui.CargoTestConfigurationEditForm

/**
 * [RunConfiguration] implementation that runs a rust module
 */
class CargoTest(project: Project,
                name: String,
                configurationType: CargoTestType)
: CargoRunConfigurationBase<CargoTestConfigurationModule>(name,
                                                          CargoTestConfigurationModule(project),
                                                          configurationType) {

    override var command: String = "test"
    override var arguments: String = ""

    private var _testName: String = ""
    var testName: String
        get() = _testName
        set(value) {
            _testName = value
            arguments = "--test " + testName
        }


    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = CargoTestConfigurationEditForm()
}

class CargoTestConfigurationModule(project: Project) : RunConfigurationModule(project)
