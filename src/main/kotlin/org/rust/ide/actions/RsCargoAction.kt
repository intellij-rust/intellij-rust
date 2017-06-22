package org.rust.ide.actions

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManagerEx
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import com.intellij.project.isEqualToProjectFileStorePath
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.createCargoCommandRunConfiguration
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.util.modulesWithCargoProject
import org.rust.ide.icons.RsIcons
import org.rust.ide.notifications.showBalloon
import org.rust.ide.utils.isNullOrEmpty

class RsCargoAction(command: String) : AnAction() {
    val command: String

    init {
        templatePresentation.text = "Run 'cargo $command'"
        this.command = command
    }

    override fun update(e: AnActionEvent) {
        if (e.project?.toolchain == null || e.project?.modulesWithCargoProject.isNullOrEmpty()) {
            e.presentation.isEnabled = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val module = getModule(e) ?: return
        val commandLine = CargoCommandLine(command)

        runCommand(module, commandLine)
    }

    private fun getModule(e: AnActionEvent): Module? {
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

        val configuration = runManager.createCargoCommandRunConfiguration(cargoCommandLine)
        configuration.isTemporary = true
        return configuration
    }
}
