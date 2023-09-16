/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.profiler

import com.intellij.execution.configurations.RunProfile
import com.intellij.profiler.clion.ProfilerExecutor
import org.rust.RsBundle
import org.rust.cargo.runconfig.RsExecutableRunner
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.profiler.dtrace.RsDTraceConfigurationExtension
import org.rust.profiler.perf.RsPerfConfigurationExtension

class RsProfilerRunner : RsExecutableRunner(ProfilerExecutor.EXECUTOR_ID, RsBundle.message("dialog.title.unable.to.run.profiler")) {
    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (profile !is CargoCommandConfiguration) return false
        return (RsDTraceConfigurationExtension.isEnabledFor() || RsPerfConfigurationExtension.isEnabledFor(profile)) &&
            super.canRun(executorId, profile)
    }

    companion object {
        const val RUNNER_ID: String = "RsProfilerRunner"
    }
}
