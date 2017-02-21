package org.rust.cargo.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.filters.Filter
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
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RustToolchain

/**
 * Manages running of general cargo commands.
 */
open class CargoRunState(
    environment: ExecutionEnvironment,
    protected val toolchain: RustToolchain,
    protected val module: Module,
    protected val cargoProjectDirectory: VirtualFile,
    val commandLine: CargoCommandLine
) : CommandLineState(environment) {

    init {
        createFilters().forEach { consoleBuilder.addFilter(it) }
    }

    override fun startProcess(): ProcessHandler {
        val cmd = toolchain.cargo(cargoProjectDirectory.path)
            .generalCommand(prepareCommandLine())
            // Explicitly use UTF-8.
            // Even though default system encoding is usually not UTF-8 on windows,
            // most Rust programs are UTF-8 only.
            .withCharset(Charsets.UTF_8)

        val handler = KillableColoredProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination
        return handler
    }

    protected open fun prepareCommandLine() = commandLine

    protected fun createFilters(): Collection<Filter> = listOf(
        RsConsoleFilter(environment.project, cargoProjectDirectory),
        RsExplainFilter(),
        RsPanicFilter(environment.project, cargoProjectDirectory),
        RsBacktraceFilter(environment.project, cargoProjectDirectory, module)
    )
}
