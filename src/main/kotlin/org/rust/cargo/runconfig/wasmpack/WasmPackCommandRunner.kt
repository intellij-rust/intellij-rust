/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.wasmpack

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import org.rust.cargo.runconfig.RsDefaultProgramRunnerBase
import org.rust.cargo.toolchain.tools.Cargo.Companion.checkNeedInstallWasmPack

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
