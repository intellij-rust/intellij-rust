/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.RunContentExecutor
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.prependArgument
import org.rust.openapiext.saveAllDocuments

class RsTestRunner : AsyncProgramRunner<RunnerSettings>() {
    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != DefaultRunExecutor.EXECUTOR_ID || profile !is CargoCommandConfiguration) {
            return false
        }
        val cleaned = profile.clean().ok ?: return false
        return cleaned.cmd.command == "test"
    }

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        saveAllDocuments()
        state as CargoRunStateBase
        val onlyBuild = "--no-run" in state.commandLine.additionalArguments
        return buildTests(environment.project, state, onlyBuild)
            .then { exitCode ->
                if (onlyBuild || exitCode != 0) return@then null
                showRunContent(state.execute(environment.executor, this), environment)
            }
    }

    override fun getRunnerId(): String = "RsTestRunner"
}

private fun buildTests(project: Project, state: CargoRunStateBase, cmdHasNoRun: Boolean): Promise<Int?> {
    val buildProcessHandler = run {
        val buildCmd = if (cmdHasNoRun) state.commandLine else state.commandLine.prependArgument("--no-run")
        val buildConfig = CargoCommandConfiguration.CleanConfiguration.Ok(buildCmd, state.config.toolchain)
        val buildState = CargoRunState(state.environment, buildConfig)
        buildState.startProcess()
    }
    val exitCode = AsyncPromise<Int?>()
    RunContentExecutor(project, buildProcessHandler)
        .apply { state.createFilters().forEach { withFilter(it) } }
        .withAfterCompletion { exitCode.setResult(buildProcessHandler.exitCode) }
        .run()
    return exitCode
}
