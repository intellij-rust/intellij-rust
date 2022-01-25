/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts.Button
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.SystemInfo
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.BuildResult
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.CargoTestRunState
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.debugger.RsDebuggerToolchainService
import org.rust.debugger.settings.RsDebuggerSettings

object RsDebugRunnerUtils {

    // TODO: move into bundle
    const val ERROR_MESSAGE_TITLE: String = "Unable to run debugger"

    fun showRunContent(
        state: CargoRunStateBase,
        environment: ExecutionEnvironment,
        runExecutable: GeneralCommandLine
    ): RunContentDescriptor {
        val runParameters = RsDebugRunParameters(
            environment.project,
            runExecutable,
            state.cargoProject,
            // TODO: always pass `withSudo` when `com.intellij.execution.process.ElevationService` supports error stream redirection
            // https://github.com/intellij-rust/intellij-rust/issues/7320
            if (state is CargoTestRunState) false else state.runConfiguration.withSudo
        )
        return XDebuggerManager.getInstance(environment.project)
            .startSession(environment, object : XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess =
                    RsLocalDebugProcess(runParameters, session, state.consoleBuilder).apply {
                        ProcessTerminatedListener.attach(processHandler, environment.project)
                        start()
                    }
            })
            .runContentDescriptor
    }

    fun checkToolchainSupported(project: Project, host: String): BuildResult.ToolchainError? {
        if (SystemInfo.isWindows) {
            if (project.toolchain is RsWslToolchain) {
                return BuildResult.ToolchainError.UnsupportedWSL
            }

            val isGNURustToolchain = "gnu" in host
            if (isGNURustToolchain) {
                return BuildResult.ToolchainError.UnsupportedGNU
            }
        }
        return null
    }

    fun checkToolchainConfigured(project: Project): Boolean {
        val lldbStatus = RsDebuggerToolchainService.getInstance().getLLDBStatus()
        val (message, action) = when (lldbStatus) {
            RsDebuggerToolchainService.LLDBStatus.Unavailable -> return false
            RsDebuggerToolchainService.LLDBStatus.NeedToDownload -> "Debugger is not loaded yet" to "Download"
            RsDebuggerToolchainService.LLDBStatus.NeedToUpdate -> "Debugger is outdated" to "Update"
            RsDebuggerToolchainService.LLDBStatus.Bundled,
            is RsDebuggerToolchainService.LLDBStatus.Binaries -> return true
        }

        val option = if (!RsDebuggerSettings.getInstance().downloadAutomatically) {
            showDialog(project, message, action)
        } else {
            Messages.OK
        }

        if (option == Messages.OK) {
            val result = RsDebuggerToolchainService.getInstance().downloadDebugger(project)
            if (result is RsDebuggerToolchainService.DownloadResult.Ok) {
                RsDebuggerSettings.getInstance().lldbPath = result.lldbDir.absolutePath
                return true
            }
        }
        return false
    }

    private fun showDialog(
        project: Project,
        @Suppress("UnstableApiUsage") @DialogMessage message: String,
        @Suppress("UnstableApiUsage") @Button action: String
    ): Int {
        return Messages.showDialog(
            project,
            message,
            ERROR_MESSAGE_TITLE,
            arrayOf(action),
            Messages.OK,
            Messages.getErrorIcon(),
            object : DialogWrapper.DoNotAskOption.Adapter() {
                override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
                    if (exitCode == Messages.OK) {
                        RsDebuggerSettings.getInstance().downloadAutomatically = isSelected
                    }
                }
            }
        )
    }
}
