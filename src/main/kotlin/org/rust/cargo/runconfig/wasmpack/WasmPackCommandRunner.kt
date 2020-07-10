/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.wasmpack

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.RsDefaultProgramRunnerBase
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.Cargo.Companion.checkNeedInstallWasmPack
import org.rust.openapiext.execute

class WasmPackCommandRunner : RsDefaultProgramRunnerBase() {
    override fun getRunnerId(): String = RUNNER_ID

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (executorId != DefaultRunExecutor.EXECUTOR_ID || profile !is WasmPackCommandConfiguration) return false
        return true
    }

    override fun execute(environment: ExecutionEnvironment) {
        if (checkNeedInstallWasmPack(environment.project)) return
        super.execute(environment)
    }

    companion object {
        const val RUNNER_ID = "WasmPackRunner"
    }
}
