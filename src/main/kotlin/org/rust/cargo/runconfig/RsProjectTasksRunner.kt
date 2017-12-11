/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.task.*
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.util.cargoProjectRoot

class RsProjectTasksRunner : ProjectTaskRunner() {
    override fun run(project: Project, context: ProjectTaskContext, callback: ProjectTaskNotification?, tasks: MutableCollection<out ProjectTask>) {
        val command = if (project.rustSettings.useCargoCheckForBuild) "check" else "build"

        for (cargoProject in project.cargoProjects.allProjects) {
            val cmd = CargoCommandLine.forProject(cargoProject, command, listOf("--all"))
            val runnerAndConfigurationSettings = RunManager.getInstance(project)
                .createCargoCommandRunConfiguration(cmd)
            val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
            //BACKCOMPAT: 2017.2
            @Suppress("DEPRECATION")
            ProgramRunnerUtil.executeConfiguration(project, runnerAndConfigurationSettings, executor)
        }
    }

    override fun canRun(projectTask: ProjectTask): Boolean =
        projectTask is ModuleBuildTask && projectTask.module.cargoProjectRoot != null

    override fun createExecutionEnvironment(project: Project, task: ExecuteRunConfigurationTask, executor: Executor?): ExecutionEnvironment? =
        null
}
