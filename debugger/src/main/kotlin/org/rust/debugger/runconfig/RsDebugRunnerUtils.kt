/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.XDebugProcessConfiguratorStarter
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import org.rust.cargo.runconfig.CargoRunStateBase

object RsDebugRunnerUtils {

    // TODO: move them into bundle
    const val ERROR_MESSAGE_TITLE: String = "Unable to run debugger"
    const val MSVC_IS_NOT_SUPPORTED_MESSAGE: String = "MSVC toolchain is not supported. Please use GNU toolchain."

    fun showRunContent(
        state: CargoRunStateBase,
        environment: ExecutionEnvironment,
        runExecutable: GeneralCommandLine
    ): RunContentDescriptor? {
        val runParameters = RsDebugRunParameters(environment.project, runExecutable, state.cargoProject)
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
}
