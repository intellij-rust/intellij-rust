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
import org.rust.cargo.runconfig.BuildResult.ToolchainError
import org.rust.cargo.runconfig.BuildResult.ToolchainError.UnsupportedMSVC
import org.rust.cargo.runconfig.CargoRunStateBase
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowEnabled
import org.rust.cargo.runconfig.legacy.RsAsyncRunner
import org.rust.cargo.runconfig.legacy.RsAsyncRunner.Companion.Binary
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

    override fun processUnsupportedToolchain(
        project: Project,
        toolchainError: ToolchainError,
        promise: AsyncPromise<Binary?>
    ) {
        project.showErrorDialog(toolchainError.message)
        promise.setResult(null)
    }

    override fun checkToolchainSupported(host: String): ToolchainError? = when {
        SystemInfo.isWindows && "msvc" in host -> UnsupportedMSVC
        else -> null
    }

    companion object {
        const val RUNNER_ID: String = "RsDebugRunnerLegacy"
    }
}
