/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManagerEx
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.runconfig.createCargoCommandRunConfiguration
import java.nio.file.Path

data class CargoCommandLine(
    val command: String, // Can't be `enum` because of custom subcommands
    val workingDirectory: Path, // Note that working directory selects Cargo project as well
    val additionalArguments: List<String> = emptyList(),
    val backtraceMode: BacktraceMode = BacktraceMode.DEFAULT,
    val channel: RustChannel = RustChannel.DEFAULT,
    val environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
    val allFeatures: Boolean = true,
    val nocapture: Boolean = false
) {

    /** Appends [arg] to Cargo options, in other words, inserts [arg] before `--` arg in [additionalArguments]. */
    fun withCargoArgument(arg: String): CargoCommandLine {
        val (cargoArgs, binaryArgs) = splitOnDoubleDash()
        if (arg in cargoArgs) return this
        return if (binaryArgs.isEmpty()) {
            copy(additionalArguments = cargoArgs + arg)
        } else {
            copy(additionalArguments = cargoArgs + arg + "--" + binaryArgs)
        }
    }

    /** Appends [arg] to binary options, in other words, inserts [arg] after `--` arg in [additionalArguments]. */
    fun withBinaryArgument(arg: String): CargoCommandLine {
        val (cargoArgs, binaryArgs) = splitOnDoubleDash()
        if (arg in binaryArgs) return this
        return copy(additionalArguments = cargoArgs + "--" + arg + binaryArgs)
    }

    /**
     * Splits [additionalArguments] into parts before and after `--`.
     * For `cargo run --release -- foo bar`, returns (["--release"], ["foo", "bar"])
     */
    fun splitOnDoubleDash(): Pair<List<String>, List<String>> =
        org.rust.cargo.util.splitOnDoubleDash(additionalArguments)

    companion object {
        fun forTargets(
            targets: List<CargoWorkspace.Target>,
            command: String,
            additionalArguments: List<String> = emptyList()
        ): CargoCommandLine {
            val pkgs = targets.map { it.pkg }
            // Make sure the selection does not span more than one package.
            assert(pkgs.map { it.rootDirectory }.distinct().size == 1)
            val pkg = pkgs.first()

            val targetArgs = targets.flatMap { target ->
                when (target.kind) {
                    CargoWorkspace.TargetKind.BIN -> listOf("--bin", target.name)
                    CargoWorkspace.TargetKind.TEST -> listOf("--test", target.name)
                    CargoWorkspace.TargetKind.EXAMPLE -> listOf("--example", target.name)
                    CargoWorkspace.TargetKind.BENCH -> listOf("--bench", target.name)
                    CargoWorkspace.TargetKind.LIB -> listOf("--lib")
                    CargoWorkspace.TargetKind.UNKNOWN -> emptyList()
                }
            }

            return CargoCommandLine(
                command,
                workingDirectory = pkg.workspace.manifestPath.parent,
                additionalArguments = listOf("--package", pkg.name) + targetArgs + additionalArguments
            )
        }

        fun forTarget(
            target: CargoWorkspace.Target,
            command: String,
            additionalArguments: List<String> = emptyList()
        ): CargoCommandLine = forTargets(listOf(target), command, additionalArguments)

        fun forProject(
            cargoProject: CargoProject,
            command: String,
            additionalArguments: List<String> = emptyList(),
            channel: RustChannel = RustChannel.DEFAULT
        ): CargoCommandLine {
            return CargoCommandLine(
                command,
                cargoProject.workingDirectory,
                additionalArguments,
                channel = channel
            )
        }
    }
}

fun CargoWorkspace.Target.launchCommand(): String? {
    return when (kind) {
        CargoWorkspace.TargetKind.BIN -> "run"
        CargoWorkspace.TargetKind.LIB -> "build"
        CargoWorkspace.TargetKind.TEST -> "test"
        CargoWorkspace.TargetKind.BENCH -> "bench"
        CargoWorkspace.TargetKind.EXAMPLE ->
            if (crateTypes.singleOrNull() == CargoWorkspace.CrateType.BIN) "run" else "build"
        else -> null
    }
}

fun CargoCommandLine.run(project: Project, cargoProject: CargoProject) {
    val runConfiguration =
        if (project.cargoProjects.allProjects.size > 1)
            createRunConfiguration(project, this, name = command + " [" + cargoProject.presentableName + "]")
        else
            createRunConfiguration(project, this)
    val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
    ProgramRunnerUtil.executeConfiguration(runConfiguration, executor)
}

private fun createRunConfiguration(
    project: Project,
    cargoCommandLine: CargoCommandLine,
    name: String? = null
): RunnerAndConfigurationSettings {
    val runManager = RunManagerEx.getInstanceEx(project)

    return runManager.createCargoCommandRunConfiguration(cargoCommandLine, name).apply {
        runManager.setTemporaryConfiguration(this)
    }
}
