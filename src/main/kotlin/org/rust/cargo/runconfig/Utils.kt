/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.cargo.toolchain.CargoCommandLine

fun CargoCommandLine.mergeWithDefault(default: CargoCommandConfiguration): CargoCommandLine =
    if (environmentVariables.envs.isEmpty())
        copy(environmentVariables = default.env)
    else
        this

fun RunManager.createCargoCommandRunConfiguration(cargoCommandLine: CargoCommandLine): RunnerAndConfigurationSettings {
    val runnerAndConfigurationSettings = createRunConfiguration(cargoCommandLine.command, CargoCommandConfigurationType().factory)
    val configuration = runnerAndConfigurationSettings.configuration as CargoCommandConfiguration
    configuration.setFromCmd(cargoCommandLine)
    return runnerAndConfigurationSettings
}
