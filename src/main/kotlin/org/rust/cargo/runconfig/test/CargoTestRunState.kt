package org.rust.cargo.runconfig.test

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.TestFrameworkRunningModel
import com.intellij.execution.testframework.autotest.ToggleAutoTestAction
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Getter
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.runconfig.CargoRunState
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RustToolchain

/**
 * Manages running of Cargo Test sessions.
 */
class CargoTestRunState(
    environment: ExecutionEnvironment,
    toolchain: RustToolchain,
    module: Module,
    cargoProjectDirectory: VirtualFile,
    commandLine: CargoCommandLine
) : CargoRunState(environment, toolchain, module, cargoProjectDirectory, commandLine) {
    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()

        val consoleProperties = CargoTestConsoleProperties(environment.runProfile as RunConfiguration, executor)
        val consoleView = SMTestRunnerConnectionUtil.createAndAttachConsole(
            "Cargo Test", processHandler, consoleProperties) as SMTRunnerConsoleView

        createFilters().forEach { consoleView.addMessageFilter(it) }
        ProcessTerminatedListener.attach(processHandler)

        val executionResult = DefaultExecutionResult(consoleView, processHandler)
        val rerunFailedTestsAction = consoleProperties.createRerunFailedTestsAction(consoleView)
        if (rerunFailedTestsAction != null) {
            rerunFailedTestsAction.setModelProvider(Getter<TestFrameworkRunningModel> { consoleView.resultsViewer })
            executionResult.setRestartActions(rerunFailedTestsAction, ToggleAutoTestAction())
        } else {
            executionResult.setRestartActions(ToggleAutoTestAction())
        }
        return executionResult
    }
}
