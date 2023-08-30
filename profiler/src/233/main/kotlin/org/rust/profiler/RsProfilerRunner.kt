/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.profiler

import com.intellij.execution.configurations.RunProfile
import com.intellij.profiler.clion.ProfilerExecutor
import com.intellij.profiler.clion.ProfilerRunChecker
import org.rust.RsBundle
import org.rust.cargo.runconfig.RsExecutableRunner

class RsProfilerRunner : RsExecutableRunner(ProfilerExecutor.EXECUTOR_ID, RsBundle.message("dialog.title.unable.to.run.profiler")) {
    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        ProfilerRunChecker.canRun() && super.canRun(executorId, profile)

    companion object {
        const val RUNNER_ID: String = "RsProfilerRunner"
        const val IJ_RUNNER_ID: String = "ProfilerRunner"
    }
}
