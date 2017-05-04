package org.rust.cargo.runconfig

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.task.*
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.util.cargoProjectRoot

class RsProjectTasksRunner : ProjectTaskRunner() {
    override fun run(project: Project, context: ProjectTaskContext, callback: ProjectTaskNotification?, tasks: MutableCollection<out ProjectTask>) {
        val cargoCommandLine = CargoCommandLine("build", listOf("--all"))
        val runnerAndConfigurationSettings = RunManager.getInstance(project)
            .createCargoCommandRunConfiguration(cargoCommandLine)
        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
        ProgramRunnerUtil.executeConfiguration(project, runnerAndConfigurationSettings, executor)
    }

    override fun canRun(projectTask: ProjectTask): Boolean =
        projectTask is ModuleBuildTask && projectTask.module.cargoProjectRoot != null

    override fun createExecutionEnvironment(project: Project, task: ExecuteRunConfigurationTask, executor: Executor?): ExecutionEnvironment? =
        null
}
