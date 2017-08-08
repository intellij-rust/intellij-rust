/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.openapi.actionSystem.AnAction
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.cargo.toolchain.CargoCommandLine

val CargoWorkspace.Target.cargoArgumentSpeck: List<String> get() {
    // Can get fancy here not add this arguments if we are the root package
    val pkgSpec = listOf("--package", pkg.name)

    val targetSpec = when (kind) {
        CargoWorkspace.TargetKind.BIN -> listOf("--bin", name)
        CargoWorkspace.TargetKind.TEST -> listOf("--test", name)
        CargoWorkspace.TargetKind.EXAMPLE -> listOf("--example", name)
        CargoWorkspace.TargetKind.BENCH -> listOf("--bench", name)
        CargoWorkspace.TargetKind.LIB -> listOf("--lib")
        CargoWorkspace.TargetKind.UNKNOWN -> return pkgSpec
    }

    return pkgSpec + targetSpec
}

fun CargoCommandLine.mergeWithDefault(default: CargoCommandLine): CargoCommandLine =
    if (environmentVariables.isEmpty())
        copy(environmentVariables = default.environmentVariables)
    else
        this

fun RunManager.createCargoCommandRunConfiguration(cargoCommandLine: CargoCommandLine): RunnerAndConfigurationSettings {
    val runnerAndConfigurationSettings = createRunConfiguration(cargoCommandLine.command, CargoCommandConfigurationType().factory)
    val configuration = runnerAndConfigurationSettings.configuration as CargoCommandConfiguration
    configuration.cargoCommandLine = cargoCommandLine
    return runnerAndConfigurationSettings
}
