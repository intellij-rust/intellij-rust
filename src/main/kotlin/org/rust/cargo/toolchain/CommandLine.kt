/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.Executor
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManagerEx
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ProgramRunner
import com.intellij.notification.NotificationType
import org.rust.RsBundle
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.RsCommandConfiguration.Companion.emulateTerminalDefault
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.runconfig.createCargoCommandRunConfiguration
import org.rust.cargo.runconfig.wasmpack.WasmPackCommandConfiguration
import org.rust.cargo.runconfig.wasmpack.WasmPackCommandConfigurationType
import org.rust.ide.notifications.RsNotifications
import org.rust.stdext.buildList
import java.io.File
import java.nio.file.Path

abstract class RsCommandLineBase {
    abstract val command: String
    abstract val workingDirectory: Path
    abstract val redirectInputFrom: File?
    abstract val additionalArguments: List<String>
    abstract val emulateTerminal: Boolean

    protected abstract val executableName: String

    protected abstract fun createRunConfiguration(runManager: RunManagerEx, name: String? = null): RunnerAndConfigurationSettings

    fun run(
        cargoProject: CargoProject,
        presentableName: String = command,
        saveConfiguration: Boolean = true,
        executor: Executor = DefaultRunExecutor.getRunExecutorInstance()
    ) {
        val project = cargoProject.project
        val configurationName = when {
            project.cargoProjects.allProjects.size > 1 -> "$presentableName [${cargoProject.presentableName}]"
            else -> presentableName
        }
        val runManager = RunManagerEx.getInstanceEx(project)
        val configuration = createRunConfiguration(runManager, configurationName).apply {
            if (saveConfiguration) {
                runManager.setTemporaryConfiguration(this)
            }
        }

        val runner = ProgramRunner.getRunner(executor.id, configuration.configuration)
        val finalExecutor = if (runner == null) {
            RsNotifications.pluginNotifications()
                .createNotification(RsBundle.message("notification.0.action.is.not.available.for.1.command", executor.actionName, "$executableName $command"), NotificationType.WARNING)
                .notify(project)
            DefaultRunExecutor.getRunExecutorInstance()
        } else {
            executor
        }

        ProgramRunnerUtil.executeConfiguration(configuration, finalExecutor)
    }
}

data class CargoCommandLine(
    override val command: String, // Can't be `enum` because of custom subcommands
    override val workingDirectory: Path, // Note that working directory selects Cargo project as well
    override val additionalArguments: List<String> = emptyList(),
    override val redirectInputFrom: File? = null,
    override val emulateTerminal: Boolean = emulateTerminalDefault,
    val backtraceMode: BacktraceMode = BacktraceMode.DEFAULT,
    val toolchain: String? = null,
    val channel: RustChannel = RustChannel.DEFAULT,
    val environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
    val requiredFeatures: Boolean = true,
    val allFeatures: Boolean = false,
    val withSudo: Boolean = false
) : RsCommandLineBase() {

    override val executableName: String
        get() = "cargo"

    override fun createRunConfiguration(runManager: RunManagerEx, name: String?): RunnerAndConfigurationSettings =
        runManager.createCargoCommandRunConfiguration(this, name)

    /**
     * Adds [arg] to [additionalArguments] as a positional argument, in other words, inserts [arg] right after
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

    fun prependArgument(arg: String): CargoCommandLine =
        copy(additionalArguments = listOf(arg) + additionalArguments)

    companion object {
        fun forTargets(
            targets: List<CargoWorkspace.Target>,
            command: String,
            additionalArguments: List<String> = emptyList(),
            usePackageOption: Boolean = true,
            isDoctest: Boolean = false
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
                    is CargoWorkspace.TargetKind.Lib -> {
                        if (isDoctest) {
                            listOf("--doc")
                        } else {
                            listOf("--lib")
                        }
                    }
                    CargoWorkspace.TargetKind.CustomBuild,
                    CargoWorkspace.TargetKind.Unknown -> emptyList()
                }
            }

            val workingDirectory = if (usePackageOption) {
                pkg.workspace.contentRoot
            } else {
                pkg.rootDirectory
            }

            val commandLineArguments = buildList {
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
            toolchain: String? = null,
            channel: RustChannel = RustChannel.DEFAULT,
            environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT
        ): CargoCommandLine = CargoCommandLine(
            command,
            workingDirectory = cargoProject.workingDirectory,
            additionalArguments = additionalArguments,
            toolchain = toolchain,
            channel = channel,
            environmentVariables = environmentVariables
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

data class WasmPackCommandLine(
    override val command: String,
    override val workingDirectory: Path,
    override val additionalArguments: List<String> = emptyList(),
    override val emulateTerminal: Boolean = emulateTerminalDefault
) : RsCommandLineBase() {

    override val executableName: String
        get() = "wasm-pack"

    override val redirectInputFrom: File? = null

    override fun createRunConfiguration(runManager: RunManagerEx, name: String?): RunnerAndConfigurationSettings {
        val runnerAndConfigurationSettings = runManager.createConfiguration(
            name ?: command,
            WasmPackCommandConfigurationType.getInstance().factory
        )
        val configuration = runnerAndConfigurationSettings.configuration as WasmPackCommandConfiguration
        configuration.setFromCmd(this)
        return runnerAndConfigurationSettings
    }
}
