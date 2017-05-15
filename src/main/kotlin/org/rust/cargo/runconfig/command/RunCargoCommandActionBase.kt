package org.rust.cargo.runconfig.command

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManagerEx
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import org.rust.cargo.runconfig.createCargoCommandRunConfiguration
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.util.modulesWithCargoProject
import javax.swing.Icon

abstract class RunCargoCommandActionBase(icon: Icon) : AnAction(icon) {
    override fun update(e: AnActionEvent) {
        val hasCargoModule = e.project?.modulesWithCargoProject.orEmpty().isNotEmpty()

        e.presentation.isEnabled = hasCargoModule
        e.presentation.isVisible = hasCargoModule
    }

    protected fun getAppropriateModule(e: AnActionEvent): Module? {
        val cargoModules = e.project?.modulesWithCargoProject.orEmpty()
        val current = e.getData(LangDataKeys.MODULE)

        return if (current in cargoModules)
            current
        else
            cargoModules.firstOrNull()
    }

    protected fun runCommand(module: Module, cargoCommandLine: CargoCommandLine) {
        val runConfiguration = createRunConfiguration(module, cargoCommandLine)
        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
        ProgramRunnerUtil.executeConfiguration(module.project, runConfiguration, executor)
    }

    private fun createRunConfiguration(module: Module, cargoCommandLine: CargoCommandLine): RunnerAndConfigurationSettings {
        val runManager = RunManagerEx.getInstanceEx(module.project)

        return runManager.createCargoCommandRunConfiguration(cargoCommandLine).apply {
            runManager.setTemporaryConfiguration(this)
        }
    }
}
