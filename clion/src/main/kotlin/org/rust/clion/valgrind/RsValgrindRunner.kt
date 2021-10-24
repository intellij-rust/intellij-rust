/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.valgrind

import com.intellij.execution.configurations.RunProfile
import com.jetbrains.cidr.cpp.valgrind.ValgrindExecutor
import org.rust.cargo.runconfig.RsExecutableRunner
import org.rust.cargo.runconfig.command.CargoCommandConfiguration

private const val ERROR_MESSAGE_TITLE: String = "Unable to run Valgrind"

class RsValgrindRunner : RsExecutableRunner(ValgrindExecutor.EXECUTOR_ID, ERROR_MESSAGE_TITLE) {
    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (profile !is CargoCommandConfiguration) return false
        return RsValgrindConfigurationExtension.isEnabledFor(profile) && super.canRun(executorId, profile)
    }

    companion object {
        const val RUNNER_ID: String = "RsValgrindRunner"
    }
}
