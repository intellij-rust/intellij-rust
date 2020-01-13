/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.RsExecutableRunner
import org.rust.debugger.runconfig.RsDebugRunnerUtils.ERROR_MESSAGE_TITLE

abstract class RsDebugRunnerBase : RsExecutableRunner(DefaultDebugExecutor.EXECUTOR_ID, ERROR_MESSAGE_TITLE) {
    override fun getRunnerId(): String = RUNNER_ID

    override fun showRunContent(
        state: CargoRunStateBase,
        environment: ExecutionEnvironment,
        runExecutable: GeneralCommandLine
    ): RunContentDescriptor? = RsDebugRunnerUtils.showRunContent(state, environment, runExecutable)

    companion object {
        const val RUNNER_ID: String = "RsDebugRunner"
    }
}
