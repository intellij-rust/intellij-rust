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
import org.rust.cargo.runconfig.console.RsConsoleBuilder
import org.rust.cargo.toolchain.tools.WasmPack
import java.io.File

class WasmPackCommandRunState(
    environment: ExecutionEnvironment,
    private val runConfiguration: WasmPackCommandConfiguration,
    private val wasmPack: WasmPack,
    private val workingDirectory: File
) : CommandLineState(environment) {

    init {
        consoleBuilder = RsConsoleBuilder(environment.project, runConfiguration)
    }

    override fun startProcess(): ProcessHandler {
        val params = ParametersListUtil.parse(runConfiguration.command)
        val commandLine = wasmPack.createCommandLine(
            workingDirectory,
            params.firstOrNull().orEmpty(),
            params.drop(1),
            runConfiguration.emulateTerminal
        )

        val handler = RsProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination
        return handler
    }
}
