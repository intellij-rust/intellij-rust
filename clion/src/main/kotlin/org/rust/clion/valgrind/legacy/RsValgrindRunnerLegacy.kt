/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.valgrind.legacy

import com.intellij.execution.configurations.RunProfile
import com.jetbrains.cidr.cpp.valgrind.ValgrindExecutor
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowAvailable
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.customBuild.CustomBuildConfiguration
import org.rust.cargo.runconfig.legacy.RsAsyncRunner
import org.rust.clion.valgrind.RsValgrindConfigurationExtension

private const val ERROR_MESSAGE_TITLE: String = "Unable to run Valgrind"

/**
 * This runner is used if [isBuildToolWindowAvailable] is false.
 */
class RsValgrindRunnerLegacy : RsAsyncRunner(ValgrindExecutor.EXECUTOR_ID, ERROR_MESSAGE_TITLE) {
    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (profile !is CargoCommandConfiguration) return false
        if (profile is CustomBuildConfiguration) return false
        return RsValgrindConfigurationExtension.isEnabledFor(profile) && super.canRun(executorId, profile)
    }

    companion object {
        const val RUNNER_ID: String = "RsValgrindRunnerLegacy"
    }
}
