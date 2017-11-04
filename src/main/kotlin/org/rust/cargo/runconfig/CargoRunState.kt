/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.SearchScopeProvider
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.console.CargoConsoleBuilder
import org.rust.cargo.runconfig.filters.RsBacktraceFilter
import org.rust.cargo.runconfig.filters.RsConsoleFilter
import org.rust.cargo.runconfig.filters.RsExplainFilter
import org.rust.cargo.runconfig.filters.RsPanicFilter
import org.rust.cargo.toolchain.Cargo
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RustToolchain

class CargoRunState(
    environment: ExecutionEnvironment,
    config: CargoCommandConfiguration.CleanConfiguration.Ok
) : CommandLineState(environment) {
    private val toolchain: RustToolchain = config.toolchain
    val commandLine: CargoCommandLine = config.cmd
    private val cargoProject: CargoProject? = CargoCommandConfiguration.findCargoProject(
        environment.project,
        commandLine.additionalArguments,
        commandLine.workingDirectory
    )

    fun cargo(): Cargo = toolchain.cargoOrWrapper(cargoProject?.manifest?.parent)

    init {
        val scope = SearchScopeProvider.createSearchScope(environment.project, environment.runProfile)
        consoleBuilder = CargoConsoleBuilder(environment.project, scope)

        consoleBuilder.addFilter(RsExplainFilter())
        val dir = cargoProject?.rootDir
        if (dir != null) {
            consoleBuilder.addFilter(RsConsoleFilter(environment.project, dir))
            consoleBuilder.addFilter(RsPanicFilter(environment.project, dir))
            consoleBuilder.addFilter(RsBacktraceFilter(environment.project, dir, cargoProject?.workspace))
        }
    }

    override fun startProcess(): ProcessHandler {
        val cmd = toolchain.cargoOrWrapper(cargoProject?.manifest?.parent)
            .toColoredCommandLine(commandLine)
            // Explicitly use UTF-8.
            // Even though default system encoding is usually not UTF-8 on windows,
            // most Rust programs are UTF-8 only.
            .withCharset(Charsets.UTF_8)

        val handler = KillableColoredProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination
        return handler
    }
}
