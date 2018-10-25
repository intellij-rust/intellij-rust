/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.SearchScopeProvider
import com.intellij.execution.filters.Filter
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

open class CargoRunState(
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

    init {
        val scope = SearchScopeProvider.createSearchScope(environment.project, environment.runProfile)
        consoleBuilder = CargoConsoleBuilder(environment.project, scope)
        createFilters().forEach { consoleBuilder.addFilter(it) }
    }

    protected fun createFilters(): Collection<Filter> {
        val filters = mutableListOf<Filter>()
        filters.add(RsExplainFilter())
        val dir = cargoProject?.workspaceRootDir ?: cargoProject?.rootDir
        if (dir != null) {
            filters.add(RsConsoleFilter(environment.project, dir))
            filters.add(RsPanicFilter(environment.project, dir))
            filters.add(RsBacktraceFilter(environment.project, dir, cargoProject?.workspace))
        }
        return filters
    }

    fun cargo(): Cargo = toolchain.cargoOrWrapper(cargoProject?.manifest?.parent)

    fun computeSysroot(): String? {
        val projectDirectory = cargoProject?.manifest?.parent ?: return null
        return toolchain.getSysroot(projectDirectory)
    }

    fun rustVersion(): RustToolchain.VersionInfo = toolchain.queryVersions()

    protected open fun prepareCommandLine() = commandLine

    override fun startProcess(): ProcessHandler {
        val cmd = toolchain.cargoOrWrapper(cargoProject?.manifest?.parent)
            .toColoredCommandLine(prepareCommandLine())
            // Explicitly use UTF-8.
            // Even though default system encoding is usually not UTF-8 on windows,
            // most Rust programs are UTF-8 only.
            .withCharset(Charsets.UTF_8)

        val handler = KillableColoredProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination
        return handler
    }
}
