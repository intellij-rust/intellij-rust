/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.target

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import org.rust.cargo.runconfig.RsExecutableRunner
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowAvailable
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.hasRemoteTarget

private const val ERROR_MESSAGE_TITLE: String = "Unable to build"

/**
 * This runner is used for remote targets if [isBuildToolWindowAvailable] is true.
 */
class RsLocalBuildForTargetRunner : RsExecutableRunner(DefaultRunExecutor.EXECUTOR_ID, ERROR_MESSAGE_TITLE) {
    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != DefaultRunExecutor.EXECUTOR_ID ||
            profile !is CargoCommandConfiguration ||
            profile.clean() !is CargoCommandConfiguration.CleanConfiguration.Ok) return false
        return profile.hasRemoteTarget &&
            profile.buildTarget.isLocal &&
            profile.isBuildToolWindowAvailable
    }

    companion object {
        const val RUNNER_ID: String = "RsLocalBuildForTargetRunner"
    }
}
