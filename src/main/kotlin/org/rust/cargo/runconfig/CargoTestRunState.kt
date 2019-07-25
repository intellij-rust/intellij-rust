/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.autotest.ToggleAutoTestAction
import org.rust.cargo.runconfig.buildtool.CargoPatch
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.console.CargoTestConsoleBuilder
import org.rust.cargo.toolchain.CargoCommandLine

class CargoTestRunState(
    environment: ExecutionEnvironment,
    runConfiguration: CargoCommandConfiguration,
    config: CargoCommandConfiguration.CleanConfiguration.Ok
) : CargoRunStateBase(environment, runConfiguration, config) {

    init {
        consoleBuilder = CargoTestConsoleBuilder(environment.runProfile as RunConfiguration, environment.executor)
        addCommandLinePatch(cargoTestPatch)
        createFilters(cargoProject).forEach { consoleBuilder.addFilter(it) }
    }

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()
        val console = createConsole(executor)
        console?.attachToProcess(processHandler)
        return DefaultExecutionResult(console, processHandler).apply { setRestartActions(ToggleAutoTestAction()) }
    }

    companion object {
        val cargoTestPatch: CargoPatch = { commandLine ->
            commandLine.copy(additionalArguments = patchArgs(commandLine))
        }

        @VisibleForTesting
        fun patchArgs(commandLine: CargoCommandLine): List<String> {
            val (pre, post) = commandLine.splitOnDoubleDash()
                .let { (pre, post) -> pre.toMutableList() to post.toMutableList() }

            val noFailFastOption = "--no-fail-fast"
            if (noFailFastOption !in pre) {
                pre.add(noFailFastOption)
            }

            val unstableOption = "-Z"
            if (unstableOption !in post) {
                post.add(unstableOption)
                post.add("unstable-options")
            }

            addFormatJsonOption(post, "--format")

            return if (post.isEmpty()) pre else pre + "--" + post
        }
    }
}
