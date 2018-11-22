/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.toolwindow.CargoToolWindow
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.cargo.toolchain.CargoCommandLine

fun CargoCommandLine.mergeWithDefault(default: CargoCommandConfiguration): CargoCommandLine =
    copy(
        backtraceMode = default.backtrace,
        channel = default.channel,
        environmentVariables = default.env,
        allFeatures = default.allFeatures,
        nocapture = default.nocapture
    )

fun RunManager.createCargoCommandRunConfiguration(cargoCommandLine: CargoCommandLine, name: String? = null): RunnerAndConfigurationSettings {
    // BACKCOMPAT: 2018.2
    @Suppress("DEPRECATION")
    val runnerAndConfigurationSettings = createRunConfiguration(name ?: cargoCommandLine.command,
        CargoCommandConfigurationType.getInstance().factory)
    val configuration = runnerAndConfigurationSettings.configuration as CargoCommandConfiguration
    configuration.setFromCmd(cargoCommandLine)
    return runnerAndConfigurationSettings
}

val Project.hasCargoProject: Boolean get() = cargoProjects.allProjects.isNotEmpty()

fun Project.buildProject() {
    val command = if (rustSettings.useCargoCheckForBuild) "check" else "build"

    val arguments = mutableListOf("--all")

    if (rustSettings.compileAllTargets) {
        val allTargets = rustSettings.toolchain
            ?.rawCargo()
            ?.checkSupportForBuildCheckAllTargets() ?: false
        if (allTargets) {
            arguments += "--all-targets"
        }
    }
    if (rustSettings.useOfflineForCargoCheck) {
        arguments += "-Zoffline"
    }

    for (cargoProject in cargoProjects.allProjects) {
        val cmd = CargoCommandLine.forProject(cargoProject, command, arguments)
        val runnerAndConfigurationSettings = RunManager.getInstance(this)
            .createCargoCommandRunConfiguration(cmd)
        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
        ProgramRunnerUtil.executeConfiguration(runnerAndConfigurationSettings, executor)
    }
}

fun getAppropriateCargoProject(dataContext: DataContext): CargoProject? {
    val cargoProjects = dataContext.getData(CommonDataKeys.PROJECT)?.cargoProjects ?: return null
    cargoProjects.allProjects.singleOrNull()?.let { return it }

    dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
        ?.let { cargoProjects.findProjectForFile(it) }
        ?.let { return it }

    return dataContext.getData(CargoToolWindow.SELECTED_CARGO_PROJECT)
        ?: cargoProjects.allProjects.firstOrNull()
}
