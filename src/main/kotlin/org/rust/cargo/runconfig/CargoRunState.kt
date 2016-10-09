package org.rust.cargo.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.toolchain.RustToolchain

class CargoRunState(
    environment: ExecutionEnvironment,
    private val toolchain: RustToolchain,
    private val cargoProjectDirectory: VirtualFile,
    private val command: String,
    private val additionalArguments: List<String>,
    private val environmentVariables: Map<String, String>
) : CommandLineState(environment) {

    init {
        consoleBuilder.addFilter(RustConsoleFilter(environment.project, cargoProjectDirectory))
        consoleBuilder.addFilter(RustExplainFilter())
        consoleBuilder.addFilter(RustPanicFilter(environment.project, cargoProjectDirectory))
        consoleBuilder.addFilter(RustBacktraceFilter(environment.project, cargoProjectDirectory))
    }

    override fun startProcess(): ProcessHandler {
        val cmd = toolchain.cargo(cargoProjectDirectory.path)
            .generalCommand(command, additionalArguments, environmentVariables)

        val handler = KillableColoredProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination
        return handler
    }
}
