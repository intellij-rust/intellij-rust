/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.DefaultProgramRunner
import org.rust.cargo.runconfig.command.CargoCommandConfiguration

class RsRunner : DefaultProgramRunner() {
    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != DefaultRunExecutor.EXECUTOR_ID || profile !is CargoCommandConfiguration) {
            return false
        }
        val cleaned = profile.clean().ok ?: return false
        return !RsTestRunner.canRunCommandLine(cleaned.cmd)
    }

    override fun getRunnerId(): String = "RustRunner"
}
