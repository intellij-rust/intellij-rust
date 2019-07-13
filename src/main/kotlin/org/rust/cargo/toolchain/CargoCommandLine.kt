/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

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
import org.rust.stdext.buildList
import java.nio.file.Path

data class CargoCommandLine(
    val command: String, // Can't be `enum` because of custom subcommands
    val workingDirectory: Path, // Note that working directory selects Cargo project as well
    val additionalArguments: List<String> = emptyList(),
    val backtraceMode: BacktraceMode = BacktraceMode.DEFAULT,
    val channel: RustChannel = RustChannel.DEFAULT,
    val environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
    val allFeatures: Boolean = false,
    val nocapture: Boolean = false,
    val emulateTerminal: Boolean = false
) {
    /**
     * Adds [arg] to [additionalArguments] as an positional argument, in other words, inserts [arg] right after
     * `--` argument in [additionalArguments].
     * */
    fun withPositionalArgument(arg: String): CargoCommandLine {
        val (pre, post) = splitOnDoubleDash()
        if (arg in post) return this
        return copy(additionalArguments = pre + "--" + arg + post)
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
            additionalArguments: List<String> = emptyList(),
            usePackageOption: Boolean = true
        ): CargoCommandLine {
            val pkgs = targets.map { it.pkg }
            // Make sure the selection does not span more than one package.
            assert(pkgs.map { it.rootDirectory }.distinct().size == 1)
            val pkg = pkgs.first()

            val targetArgs = targets.distinctBy { it.name }.flatMap { target ->
                when (target.kind) {
                    CargoWorkspace.TargetKind.Bin -> listOf("--bin", target.name)
                    CargoWorkspace.TargetKind.Test -> listOf("--test", target.name)
                    CargoWorkspace.TargetKind.ExampleBin, is CargoWorkspace.TargetKind.ExampleLib ->
                        listOf("--example", target.name)
                    CargoWorkspace.TargetKind.Bench -> listOf("--bench", target.name)
                    is CargoWorkspace.TargetKind.Lib -> listOf("--lib")
                    CargoWorkspace.TargetKind.Unknown -> emptyList()
                }
            }

            val workingDirectory = if (usePackageOption) {
                pkg.workspace.contentRoot
            } else {
                pkg.rootDirectory
            }

            val commandLineArguments = buildList<String> {
                if (usePackageOption) {
                    add("--package")
                    add(pkg.name)
                }
                addAll(targetArgs)
                addAll(additionalArguments)
            }

            return CargoCommandLine(command, workingDirectory, commandLineArguments)
        }

        fun forTarget(
            target: CargoWorkspace.Target,
            command: String,
            additionalArguments: List<String> = emptyList(),
            usePackageOption: Boolean = true
        ): CargoCommandLine = forTargets(listOf(target), command, additionalArguments, usePackageOption)

        fun forProject(
            cargoProject: CargoProject,
            command: String,
            additionalArguments: List<String> = emptyList(),
            channel: RustChannel = RustChannel.DEFAULT
        ): CargoCommandLine = CargoCommandLine(
            command,
            workingDirectory = cargoProject.workingDirectory,
            additionalArguments = additionalArguments,
            channel = channel
        )

        fun forPackage(
            cargoPackage: CargoWorkspace.Package,
            command: String,
            additionalArguments: List<String> = emptyList()
        ): CargoCommandLine = CargoCommandLine(
            command,
            workingDirectory = cargoPackage.workspace.manifestPath.parent,
            additionalArguments = listOf("--package", cargoPackage.name) + additionalArguments
        )
    }
}

fun CargoWorkspace.Target.launchCommand(): String? {
    return when (kind) {
        CargoWorkspace.TargetKind.Bin -> "run"
        is CargoWorkspace.TargetKind.Lib -> "build"
        CargoWorkspace.TargetKind.Test -> "test"
        CargoWorkspace.TargetKind.Bench -> "bench"
        CargoWorkspace.TargetKind.ExampleBin -> "run"
        is CargoWorkspace.TargetKind.ExampleLib -> "build"
        else -> null
    }
}

fun CargoCommandLine.run(
    cargoProject: CargoProject,
    presentableName: String = command,
    saveConfiguration: Boolean = true
) {
    val project = cargoProject.project
    val name = if (project.cargoProjects.allProjects.size > 1) {
        "$presentableName [${cargoProject.presentableName}]"
    } else {
        presentableName
    }
    val configuration = createRunConfiguration(project, this, name, saveConfiguration)
    val executor = DefaultRunExecutor.getRunExecutorInstance()
    ProgramRunnerUtil.executeConfiguration(configuration, executor)
}

private fun createRunConfiguration(
    project: Project,
    cargoCommandLine: CargoCommandLine,
    name: String? = null,
    saveConfiguration: Boolean
): RunnerAndConfigurationSettings {
    val runManager = RunManagerEx.getInstanceEx(project)
    return runManager.createCargoCommandRunConfiguration(cargoCommandLine, name).apply {
        if (saveConfiguration) {
            runManager.setTemporaryConfiguration(this)
        }
    }
}

fun CargoCommandLine.prependArgument(arg: String): CargoCommandLine =
    copy(additionalArguments = listOf(arg) + additionalArguments)
