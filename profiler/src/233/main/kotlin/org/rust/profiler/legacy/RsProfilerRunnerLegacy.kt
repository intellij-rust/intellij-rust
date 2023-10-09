/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.profiler.legacy

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.profiler.clion.ProfilerExecutor
import com.intellij.profiler.clion.ProfilerRunChecker
import org.rust.RsBundle
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowAvailable
import org.rust.cargo.runconfig.legacy.RsAsyncRunner
import org.rust.cargo.toolchain.wsl.RsWslToolchain

@NlsContexts.DialogTitle
private val ERROR_MESSAGE_TITLE: String = RsBundle.message("dialog.title.unable.to.run.profiler")

/**
 * This runner is used if [isBuildToolWindowAvailable] is false.
 */
class RsProfilerRunnerLegacy : RsAsyncRunner(ProfilerExecutor.EXECUTOR_ID, ERROR_MESSAGE_TITLE) {
    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (!ProfilerRunChecker.canRun()) return false

        if (SystemInfo.isWindows) {
            val toolchain = (profile as? RunConfiguration)?.project?.toolchain
            if (toolchain !is RsWslToolchain) return false
        }

        return super.canRun(executorId, profile)
    }

    companion object {
        const val RUNNER_ID: String = "RsProfilerRunnerLegacy"
    }
}
