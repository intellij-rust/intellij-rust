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
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts.Button
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.SystemInfo
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.BuildResult.ToolchainError
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.CargoTestRunState
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.debugger.DebuggerAvailability
import org.rust.debugger.DebuggerKind
import org.rust.debugger.RsDebuggerToolchainService
import org.rust.debugger.settings.RsDebuggerSettings

object RsDebugRunnerUtils {

    @Nls
    val ERROR_MESSAGE_TITLE: String = RsBundle.message("unable.to.run.debugger")

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
            if (state is CargoTestRunState) false else state.runConfiguration.withSudo,
            state.runConfiguration.emulateTerminal
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

    fun checkToolchainSupported(project: Project, host: String): ToolchainError? {
        if (SystemInfo.isWindows) {
            if (project.toolchain is RsWslToolchain) {
                return ToolchainError.UnsupportedWSL
            }

            val isGNURustToolchain = "gnu" in host
            val isMSVCRustToolchain = "msvc" in host
            val isGdbAvailable = RsDebuggerToolchainService.getInstance().gdbAvailability() !is DebuggerAvailability.Unavailable
            val debuggerKind = RsDebuggerSettings.getInstance().debuggerKind

            return when {
                isGNURustToolchain && !isGdbAvailable -> ToolchainError.UnsupportedGNU
                isGNURustToolchain && debuggerKind == DebuggerKind.LLDB -> ToolchainError.MSVCWithRustGNU
                isMSVCRustToolchain && debuggerKind == DebuggerKind.GDB -> ToolchainError.GNUWithRustMSVC
                else -> null
            }
        }
        return null
    }

    fun checkToolchainConfigured(project: Project): Boolean {
        val debuggerKind = RsDebuggerSettings.getInstance().debuggerKind
        val debuggerAvailability = RsDebuggerToolchainService.getInstance().debuggerAvailability(debuggerKind)

        val (message, action) = when (debuggerAvailability) {
            DebuggerAvailability.Unavailable -> return false
            DebuggerAvailability.NeedToDownload -> "Debugger is not loaded yet" to "Download"
            DebuggerAvailability.NeedToUpdate -> "Debugger is outdated" to "Update"
            DebuggerAvailability.Bundled,
            is DebuggerAvailability.Binaries -> return true
        }

        val downloadDebugger = if (!RsDebuggerSettings.getInstance().downloadAutomatically) {
            showDialog(project, message, action)
        } else {
            true
        }

        if (downloadDebugger) {
            val result = RsDebuggerToolchainService.getInstance().downloadDebugger(project, debuggerKind)
            if (result is RsDebuggerToolchainService.DownloadResult.Ok) {
                return true
            }
        }
        return false
    }

    private fun showDialog(
        project: Project,
        @Suppress("UnstableApiUsage") @DialogMessage message: String,
        @Suppress("UnstableApiUsage") @Button action: String
    ): Boolean {
        val doNotAsk = object : DoNotAskOption.Adapter() {
            override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
                if (exitCode == Messages.OK) {
                    RsDebuggerSettings.getInstance().downloadAutomatically = isSelected
                }
            }
        }

        return MessageDialogBuilder.okCancel(ERROR_MESSAGE_TITLE, message)
            .yesText(action)
            .icon(Messages.getErrorIcon())
            .doNotAsk(doNotAsk)
            .ask(project)
    }
}
