/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.text.nullize
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.runconfig.buildtool.CargoPatch
import org.rust.cargo.runconfig.buildtool.cargoPatches
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.runconfig.target.languageRuntime
import org.rust.cargo.runconfig.target.startProcess
import org.rust.cargo.runconfig.target.targetEnvironment
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.toolchain.tools.cargoOrWrapper
import org.rust.cargo.toolchain.tools.rustc
import java.nio.file.Path

abstract class CargoRunStateBase(
    environment: ExecutionEnvironment,
    val runConfiguration: CargoCommandConfiguration,
    val config: CargoCommandConfiguration.CleanConfiguration.Ok
) : CommandLineState(environment) {
    val project: Project = environment.project
    val toolchain: RsToolchainBase = config.toolchain
    val commandLine: CargoCommandLine = config.cmd
    val cargoProject: CargoProject? = CargoCommandConfiguration.findCargoProject(
        project,
        commandLine.additionalArguments,
        commandLine.workingDirectory
    )
    private val workingDirectory: Path? get() = cargoProject?.workingDirectory

    protected val commandLinePatches: MutableList<CargoPatch> = mutableListOf()

    init {
        commandLinePatches.addAll(environment.cargoPatches)
    }

    fun cargo(): Cargo = toolchain.cargoOrWrapper(workingDirectory)

    fun rustVersion(): RustcVersion? = toolchain.rustc().queryVersion(workingDirectory)

    fun prepareCommandLine(vararg additionalPatches: CargoPatch): CargoCommandLine {
        var commandLine = commandLine
        for (patch in commandLinePatches) {
            commandLine = patch(commandLine)
        }
        for (patch in additionalPatches) {
            commandLine = patch(commandLine)
        }
        return commandLine
    }

    override fun startProcess(): ProcessHandler = startProcess(processColors = true)

    /**
     * @param processColors if true, process ANSI escape sequences, otherwise keep escape codes in the output
     */
    fun startProcess(processColors: Boolean): ProcessHandler {
        val targetEnvironment = runConfiguration.targetEnvironment
        // Fallback to non-target implementation in case of local target
        if (targetEnvironment == null) {
            val commandLine = cargo().toColoredCommandLine(environment.project, prepareCommandLine())
            LOG.debug("Executing Cargo command: `${commandLine.commandLineString}`")
            val handler = RsProcessHandler(commandLine, processColors)
            ProcessTerminatedListener.attach(handler) // shows exit code upon termination
            return handler
        }

        val remoteRunPatch: CargoPatch = { commandLine ->
            if (runConfiguration.buildTarget.isRemote && targetEnvironment.typeId == SSH_TARGET_TYPE_ID) {
                commandLine.prependArgument("--target-dir=${targetEnvironment.projectRootOnTarget}/target")
            } else {
                commandLine
            }.copy(emulateTerminal = false)
        }

        val commandLine = cargo().toColoredCommandLine(project, prepareCommandLine(remoteRunPatch))
        commandLine.exePath = targetEnvironment.languageRuntime?.cargoPath.nullize(true) ?: "cargo"
        return commandLine.startProcess(project, targetEnvironment, processColors, uploadExecutable = false)
    }

    companion object {
        private val LOG: Logger = logger<CargoRunStateBase>()

        private const val SSH_TARGET_TYPE_ID: String = "ssh/sftp"
    }
}
