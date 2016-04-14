package org.rust.cargo.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.commands.Cargo

class CargoRunState(environment: ExecutionEnvironment,
                    private val pathToCargo: String,
                    private val cargoProjectDirectory: VirtualFile,
                    private val command: String,
                    private val additionalArguments: List<String>,
                    private val environmentVariables: Map<String, String>) : CommandLineState(environment) {

    init {
        consoleBuilder.addFilter(RustConsoleFilter(environment.project, cargoProjectDirectory))
    }

    override fun startProcess(): ProcessHandler {
        val cmd = Cargo(pathToCargo, cargoProjectDirectory.path).generalCommand(command, additionalArguments, environmentVariables)
        return OSProcessHandler(cmd)
    }
}
