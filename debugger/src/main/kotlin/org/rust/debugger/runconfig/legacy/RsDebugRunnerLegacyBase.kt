/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.runconfig.legacy

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.concurrency.AsyncPromise
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowEnabled
import org.rust.cargo.runconfig.legacy.RsAsyncRunner
import org.rust.debugger.runconfig.RsDebugRunnerUtils
import org.rust.debugger.runconfig.RsDebugRunnerUtils.ERROR_MESSAGE_TITLE

/**
 * This runner is used if [isBuildToolWindowEnabled] is false.
 */
abstract class RsDebugRunnerLegacyBase : RsAsyncRunner(DefaultDebugExecutor.EXECUTOR_ID, ERROR_MESSAGE_TITLE) {
    override fun getRunnerId(): String = RUNNER_ID

    override fun getRunContentDescriptor(
        state: CargoRunStateBase,
        environment: ExecutionEnvironment,
        runCommand: GeneralCommandLine
    ): RunContentDescriptor? = RsDebugRunnerUtils.showRunContent(state, environment, runCommand)


    override fun processUnsupportedToolchain(project: Project, promise: AsyncPromise<RsAsyncRunner.Companion.Binary?>) {
        project.showErrorDialog(RsDebugRunnerUtils.MSVC_IS_NOT_SUPPORTED_MESSAGE)
        promise.setResult(null)
    }

    override fun checkToolchainSupported(state: CargoRunStateBase): Boolean =
        !(SystemInfo.isWindows && "msvc" in state.rustVersion().rustc?.host.orEmpty())

    companion object {
        const val RUNNER_ID: String = "RsDebugRunnerLegacy"
    }
}
