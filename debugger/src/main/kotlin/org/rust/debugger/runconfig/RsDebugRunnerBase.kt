/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.RsExecutableRunner
import org.rust.cargo.toolchain.Cargo
import org.rust.cargo.util.CargoArgsParser
import org.rust.debugger.runconfig.RsDebugRunnerUtils.ERROR_MESSAGE_TITLE
import org.rust.openapiext.withWorkDirectory

abstract class RsDebugRunnerBase : RsExecutableRunner(DefaultDebugExecutor.EXECUTOR_ID, ERROR_MESSAGE_TITLE) {
    override fun getRunnerId(): String = RUNNER_ID

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        if (state !is CargoRunStateBase) return null

        val binaries = environment.binaries.orEmpty()
        val errorMessage = when {
            binaries.isEmpty() -> "Can't find a binary."
            binaries.size > 1 -> "More than one binary was produced. " +
                "Please specify `--bin`, `--lib`, `--test` or `--example` flag explicitly."
            else -> null
        }
        if (errorMessage != null) {
            environment.project.showErrorDialog(errorMessage)
            return null
        }

        val runCargoCommand = state.prepareCommandLine()
        val (_, executableArguments) = CargoArgsParser.parseArgs(runCargoCommand.command, runCargoCommand.additionalArguments)
        val runExecutable = GeneralCommandLine(binaries.single().toString(), *executableArguments.toTypedArray())
            .withWorkDirectory(runCargoCommand.workingDirectory)
        Cargo.configureCommandLine(
            runExecutable,
            runCargoCommand.backtraceMode,
            runCargoCommand.environmentVariables,
            runCargoCommand.emulateTerminal
        )
        return showRunContent(state, environment, runExecutable)
    }


    override fun showRunContent(
        state: CargoRunStateBase,
        environment: ExecutionEnvironment,
        runExecutable: GeneralCommandLine
    ): RunContentDescriptor? = RsDebugRunnerUtils.showRunContent(state, environment, runExecutable)

    companion object {
        const val RUNNER_ID: String = "RsDebugRunner"
    }
}
