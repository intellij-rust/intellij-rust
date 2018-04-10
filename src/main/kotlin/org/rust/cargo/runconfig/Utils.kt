/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.cargo.toolchain.CargoCommandLine

private val LOG_GENERAL_COMMAND_LINE = Logger.getInstance(GeneralCommandLine::class.java)

fun GeneralCommandLine.runExecutable(): List<String>? {
    val procOut = try {
        val timeoutMs = 1000
        CapturingProcessHandler(this).runProcess(timeoutMs)
    } catch (e: ExecutionException) {
        LOG_GENERAL_COMMAND_LINE.warn("Failed to run executable!", e)
        return null
    }

    if (procOut.exitCode != 0 || procOut.isCancelled || procOut.isTimeout)
        return null

    return procOut.stdoutLines
}

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

val Project.hasCargoProject: Boolean get() = cargoProjects.allProjects.isNotEmpty()

fun Project.buildProject() {
    val command = if (rustSettings.useCargoCheckForBuild) "check" else "build"
    val allTargets = rustSettings.toolchain
        ?.rawCargo()
        ?.checkSupportForBuildCheckAllTargets() ?: false
    val arguments = mutableListOf("--all")

    if (allTargets) {
        arguments += "--all-targets"
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
