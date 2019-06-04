/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.filters.Filter
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.runconfig.filters.RsBacktraceFilter
import org.rust.cargo.runconfig.filters.RsConsoleFilter
import org.rust.cargo.runconfig.filters.RsExplainFilter
import org.rust.cargo.runconfig.filters.RsPanicFilter
import org.rust.cargo.toolchain.Cargo
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RustToolchain
import java.nio.file.Path

abstract class CargoRunStateBase(
    environment: ExecutionEnvironment,
    val runConfiguration: CargoCommandConfiguration,
    val config: CargoCommandConfiguration.CleanConfiguration.Ok
) : CommandLineState(environment) {
    val toolchain: RustToolchain = config.toolchain
    val commandLine: CargoCommandLine = config.cmd
    val cargoProject: CargoProject? = CargoCommandConfiguration.findCargoProject(
        environment.project,
        commandLine.additionalArguments,
        commandLine.workingDirectory
    )
    private val workingDirectory: Path? get() = cargoProject?.workingDirectory

    fun createFilters(): Collection<Filter> {
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

    fun cargo(): Cargo = toolchain.cargoOrWrapper(workingDirectory)

    fun computeSysroot(): String? = workingDirectory?.let { toolchain.getSysroot(it) }

    fun rustVersion(): RustToolchain.VersionInfo = toolchain.queryVersions()

    open fun prepareCommandLine(): CargoCommandLine = commandLine

    public override fun startProcess(): ProcessHandler {
        val cmd = toolchain.cargoOrWrapper(workingDirectory)
            .toColoredCommandLine(prepareCommandLine())
        val handler = RsKillableColoredProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination
        return handler
    }
}
