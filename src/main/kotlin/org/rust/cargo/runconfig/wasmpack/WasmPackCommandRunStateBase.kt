/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.wasmpack

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.runconfig.RsProcessHandler
import org.rust.cargo.toolchain.tools.WasmPack
import java.io.File

abstract class WasmPackCommandRunStateBase(
    environment: ExecutionEnvironment,
    val runConfiguration: WasmPackCommandConfiguration,
    val wasmPack: WasmPack,
    val workingDirectory: File
) : CommandLineState(environment) {
    override fun startProcess(): ProcessHandler {
        val params = ParametersListUtil.parse(runConfiguration.command)
        val commandLine = wasmPack.createCommandLine(
            workingDirectory,
            params.firstOrNull().orEmpty(),
            params.drop(1)
        )

        val handler = RsProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination
        return handler
    }
}
