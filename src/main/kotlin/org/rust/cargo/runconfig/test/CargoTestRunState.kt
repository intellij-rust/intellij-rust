package org.rust.cargo.runconfig.test

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.TestFrameworkRunningModel
import com.intellij.execution.testframework.autotest.ToggleAutoTestAction
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
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

    override fun prepareCommandLine(): CargoCommandLine =
        commandLine.copy(additionalArguments = patchArgs(commandLine.additionalArguments))

    companion object {
        /**
         * This method is @TestOnly because it should be private, but there are unit tests for it.
         *
         * Libtest likes to yell when there are duplicated
         * args, so we cannot just insert our defaults :(
         */
        @TestOnly
        fun patchArgs(args: List<String>): List<String> {
            val ddIdx = args.indexOf("--")
            val (cargoArgs, libtestArgs) =
                if (ddIdx < 0) {
                    args.toMutableList() to mutableListOf()
                } else {
                    args.subList(0, ddIdx).toMutableList() to args.subList(ddIdx + 1, args.size).toMutableList()
                }

            val nocapture = "--nocapture"
            val noFailFast = "--no-fail-fast"
            val testThreads = "--test-threads"

            if (noFailFast !in cargoArgs) cargoArgs.add(noFailFast)

            if (nocapture !in libtestArgs) libtestArgs.add(nocapture)

            if (testThreads !in libtestArgs) {
                libtestArgs.add(testThreads)
                libtestArgs.add("1")
            } else {
                val idx = libtestArgs.indexOf(testThreads)
                if (idx < libtestArgs.size - 1) {
                    if (libtestArgs[idx + 1].all(StringUtil::isDecimalDigit)) {
                        libtestArgs[idx + 1] = "1"
                    } else {
                        libtestArgs.add(idx + 1, "1")
                    }
                } else {
                    libtestArgs.add("1")
                }
            }

            return cargoArgs + "--" + libtestArgs
        }
    }
}
