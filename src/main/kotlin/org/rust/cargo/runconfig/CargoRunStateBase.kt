/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.runconfig.buildtool.CargoPatch
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.workingDirectory
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

    private val commandLinePatches: MutableList<CargoPatch> = mutableListOf()

    fun addCommandLinePatch(patch: CargoPatch) {
        commandLinePatches.add(patch)
    }

    fun cargo(): Cargo = toolchain.cargoOrWrapper(workingDirectory)

    fun rustVersion(): RustToolchain.VersionInfo = toolchain.queryVersions()

    fun prepareCommandLine(): CargoCommandLine {
        var commandLine = commandLine
        for (patch in commandLinePatches) {
            commandLine = patch(commandLine)
        }
        return commandLine
    }

    override fun startProcess(): ProcessHandler = startProcess(emulateTerminal = false)

    fun startProcess(emulateTerminal: Boolean): ProcessHandler {
        var commandLine = cargo().toColoredCommandLine(environment.project, prepareCommandLine())
        if (emulateTerminal) {
            commandLine = PtyCommandLine(commandLine)
                .withInitialColumns(PtyCommandLine.MAX_COLUMNS)
                .withConsoleMode(false)
        }
        val handler = RsKillableColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler) // shows exit code upon termination
        return handler
    }
}
