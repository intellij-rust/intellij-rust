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
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.invokeLater
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowAvailable
import org.rust.cargo.runconfig.buildtool.isActivateToolWindowBeforeRun
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.saveAllDocuments

class CargoTestCommandRunner : AsyncProgramRunner<RunnerSettings>() {
    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != DefaultRunExecutor.EXECUTOR_ID || profile !is CargoCommandConfiguration) return false
        val cleaned = profile.clean().ok ?: return false
        val isLocalRun = !profile.hasRemoteTarget || profile.buildTarget.isRemote
        val isLegacyTestRun = !profile.isBuildToolWindowAvailable &&
            cleaned.cmd.command == "test" &&
            getBuildConfiguration(profile) != null
        return isLocalRun && isLegacyTestRun
    }

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        saveAllDocuments()
        val onlyBuild = "--no-run" in (state as CargoRunStateBase).commandLine.additionalArguments
        return buildTests(environment, state, onlyBuild)
            .then { exitCode ->
                if (onlyBuild || exitCode != 0) return@then null
                showRunContent(state.execute(environment.executor, this), environment)
            }
    }

    companion object {
        const val RUNNER_ID: String = "CargoTestCommandRunner"

        private fun buildTests(environment: ExecutionEnvironment, state: CargoRunStateBase, cmdHasNoRun: Boolean): Promise<Int?> {
            val buildProcessHandler = run {
                val buildCmd = state.commandLine.copy(emulateTerminal = false, withSudo = false).run {
                    if (cmdHasNoRun) this else prependArgument("--no-run")
                }
                val buildConfig = CargoCommandConfiguration.CleanConfiguration.Ok(buildCmd, state.config.toolchain)
                val buildState = CargoRunState(state.environment, state.runConfiguration, buildConfig)
                buildState.startProcess(processColors = true)
            }
            val exitCode = AsyncPromise<Int?>()

            if (environment.isActivateToolWindowBeforeRun && !isUnitTestMode) {
                RunContentExecutor(environment.project, buildProcessHandler)
                    .apply { createFilters(state.cargoProject).forEach { withFilter(it) } }
                    .withAfterCompletion { exitCode.setResult(buildProcessHandler.exitCode) }
                    .run()
            } else {
                buildProcessHandler.addProcessListener(object : ProcessAdapter() {
                    override fun processTerminated(event: ProcessEvent) {
                        invokeLater {
                            exitCode.setResult(buildProcessHandler.exitCode)
                        }
                    }
                })
                buildProcessHandler.startNotify()
            }
            return exitCode
        }
    }
}
