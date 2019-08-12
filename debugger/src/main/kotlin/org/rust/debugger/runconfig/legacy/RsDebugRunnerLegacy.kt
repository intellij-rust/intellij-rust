/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig.legacy

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugProcessConfiguratorStarter
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.cpp.toolchains.CPPToolchainsConfigurable
import com.jetbrains.cidr.toolchains.OSType
import org.jetbrains.concurrency.AsyncPromise
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowEnabled
import org.rust.cargo.runconfig.legacy.RsAsyncRunner
import org.rust.debugger.runconfig.RsDebugRunParameters
import org.rust.debugger.runconfig.RsLocalDebugProcess

private const val ERROR_MESSAGE_TITLE: String = "Unable to run debugger"

/**
 * This runner is used if [isBuildToolWindowEnabled] is false.
 */
class RsDebugRunnerLegacy : RsAsyncRunner(DefaultDebugExecutor.EXECUTOR_ID, ERROR_MESSAGE_TITLE) {
    override fun getRunnerId(): String = RUNNER_ID

    override fun getRunContentDescriptor(
        state: CargoRunStateBase,
        environment: ExecutionEnvironment,
        runCommand: GeneralCommandLine
    ): RunContentDescriptor? {
        val runParameters = RsDebugRunParameters(environment.project, runCommand, state.cargoProject)
        return XDebuggerManager.getInstance(environment.project)
            .startSession(environment, object : XDebugProcessConfiguratorStarter() {
                override fun start(session: XDebugSession): XDebugProcess =
                    RsLocalDebugProcess(runParameters, session, state.consoleBuilder).apply {
                        ProcessTerminatedListener.attach(processHandler, environment.project)
                        setupDebugSession(state)
                        start()
                    }

                override fun configure(data: XDebugSessionData?) {}
            })
            .runContentDescriptor
    }

    override fun checkToolchainConfigured(project: Project): Boolean {
        val toolchains = CPPToolchains.getInstance()
        val toolchain = toolchains.defaultToolchain
        if (toolchain == null) {
            val option = Messages.showDialog(
                project,
                "Debug toolchain is not configured.",
                ERROR_MESSAGE_TITLE,
                arrayOf("Configure"),
                Messages.OK,
                Messages.getErrorIcon()
            )
            if (option == Messages.OK) {
                ShowSettingsUtil.getInstance().showSettingsDialog(
                    project,
                    CPPToolchainsConfigurable::class.java,
                    null
                )
            }
            return false
        }
        return true
    }

    override fun checkToolchainSupported(state: CargoRunStateBase): Boolean =
        !(CPPToolchains.getInstance().osType == OSType.WIN && "msvc" in state.rustVersion().rustc?.host.orEmpty())

    override fun processUnsupportedToolchain(project: Project, promise: AsyncPromise<RsAsyncRunner.Companion.Binary?>) {
        project.showErrorDialog("MSVC toolchain is not supported. Please use GNU toolchain.")
        promise.setResult(null)
    }

    companion object {
        const val RUNNER_ID: String = "RsDebugRunnerLegacy"
    }
}
