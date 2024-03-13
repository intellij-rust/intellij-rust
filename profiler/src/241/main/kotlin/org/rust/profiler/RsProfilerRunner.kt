/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.profiler

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.util.SystemInfo
import com.intellij.profiler.clion.ProfilerExecutor
import com.intellij.profiler.clion.ProfilerRunChecker
import org.rust.RsBundle
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.RsExecutableRunner
import org.rust.cargo.toolchain.wsl.RsWslToolchain

class RsProfilerRunner : RsExecutableRunner(ProfilerExecutor.EXECUTOR_ID, RsBundle.message("dialog.title.unable.to.run.profiler")) {
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
        const val RUNNER_ID: String = "RsProfilerRunner"
        const val IJ_RUNNER_ID: String = "ProfilerRunner"
    }
}
