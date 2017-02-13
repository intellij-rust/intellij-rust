package org.rust.cargo.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.runconfig.filters.RsBacktraceFilter
import org.rust.cargo.runconfig.filters.RsConsoleFilter
import org.rust.cargo.runconfig.filters.RsExplainFilter
import org.rust.cargo.runconfig.filters.RsPanicFilter
import org.rust.cargo.toolchain.RustToolchain

class CargoRunState(
    environment: ExecutionEnvironment,
    private val toolchain: RustToolchain,
    module: Module,
    private val cargoProjectDirectory: VirtualFile,
    private val command: String,
    private val additionalArguments: List<String>,
    private val environmentVariables: Map<String, String>
) : CommandLineState(environment) {

    init {
        consoleBuilder.addFilter(RsConsoleFilter(environment.project, cargoProjectDirectory))
        consoleBuilder.addFilter(RsExplainFilter())
        consoleBuilder.addFilter(RsPanicFilter(environment.project, cargoProjectDirectory))
        consoleBuilder.addFilter(RsBacktraceFilter(environment.project, cargoProjectDirectory, module))
    }

    override fun startProcess(): ProcessHandler {
        val cmd = toolchain.cargo(cargoProjectDirectory.path)
            .generalCommand(command, additionalArguments, environmentVariables)
            // Explicitly use UTF-8.
            // Even though default system encoding is usually not UTF-8 on windows,
            // most Rust programs are UTF-8 only.
            .withCharset(Charsets.UTF_8)

        val handler = KillableColoredProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination
        return handler
    }
}
