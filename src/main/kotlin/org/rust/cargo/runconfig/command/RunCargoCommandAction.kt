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
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.runconfig.createCargoCommandRunConfiguration
import org.rust.cargo.runconfig.ui.RunCargoCommandDialog
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.util.modulesWithCargoProject

class RunCargoCommandAction : AnAction(CargoIcons.ICON) {
    override fun update(e: AnActionEvent) {
        val hasCargoModule = e.project?.modulesWithCargoProject.orEmpty().isNotEmpty()

        e.presentation.isEnabled = hasCargoModule
        e.presentation.isVisible = hasCargoModule
    }

    override fun actionPerformed(e: AnActionEvent) {
        val module = getAppropriateModule(e) ?: return
        val dialog = RunCargoCommandDialog(module.project)

        if (!dialog.showAndGet()) return

        runCommand(module, dialog.getCargoCommandLine())
    }

    private fun getAppropriateModule(e: AnActionEvent): Module? {
        val cargoModules = e.project?.modulesWithCargoProject.orEmpty()
        val current = e.getData(LangDataKeys.MODULE)

        return if (current in cargoModules)
            current
        else
            cargoModules.firstOrNull()
    }

    private fun runCommand(module: Module, cargoCommandLine: CargoCommandLine) {
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

