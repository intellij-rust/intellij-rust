/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.execution.Executor
import com.intellij.execution.InputRedirectAware
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.actions.ConsolePropertiesProvider
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.util.ProgramParametersUtil
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.execution.ParametersListUtil
import org.jdom.Element
import org.rust.RsBundle
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.*
import org.rust.cargo.runconfig.test.CargoTestConsoleProperties
import org.rust.cargo.runconfig.ui.CargoCommandConfigurationEditor
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.toolchain.tools.isRustupAvailable
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * This class describes a Run Configuration.
 * It is basically a bunch of values which are persisted to .xml files inside .idea,
 * or displayed in the GUI form. It has to be mutable to satisfy various IDE's APIs.
 */
open class CargoCommandConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : RsCommandConfiguration(project, name, factory),
    InputRedirectAware.InputRedirectOptions, ConsolePropertiesProvider {
    override var command: String = "run"
    var channel: RustChannel = RustChannel.DEFAULT
    var requiredFeatures: Boolean = true
    var allFeatures: Boolean = false
    var emulateTerminal: Boolean = false
    var withSudo: Boolean = false
    var backtrace: BacktraceMode = BacktraceMode.SHORT
    var env: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

    private var isRedirectInput: Boolean = false
    private var redirectInputPath: String? = null
    private val redirectInputFile: File?
        get() {
            if (!isRedirectInput) return null
            if (redirectInputPath?.isNotEmpty() != true) return null
            val redirectInputPath = FileUtil.toSystemDependentName(ProgramParametersUtil.expandPathAndMacros(redirectInputPath, null, project))
            var file = File(redirectInputPath)
            if (!file.isAbsolute && workingDirectory != null) {
                file = File(File(workingDirectory.toString()), redirectInputPath)
            }
            return file
        }

    override fun isRedirectInput(): Boolean = isRedirectInput

    override fun setRedirectInput(value: Boolean) {
        isRedirectInput = value
    }

    override fun getRedirectInputPath(): String? = redirectInputPath

    override fun setRedirectInputPath(value: String?) {
        redirectInputPath = value
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeEnum("channel", channel)
        element.writeBool("requiredFeatures", requiredFeatures)
        element.writeBool("allFeatures", allFeatures)
        element.writeBool("emulateTerminal", emulateTerminal)
        element.writeBool("withSudo", withSudo)
        element.writeEnum("backtrace", backtrace)
        env.writeExternal(element)
        element.writeBool("isRedirectInput", isRedirectInput)
        element.writeString("redirectInputPath", redirectInputPath ?: "")
    }

    /**
     * If you change serialization, make sure that the old variant is still
     * readable for several releases.
     */
    override fun readExternal(element: Element) {
        super.readExternal(element)
        element.readEnum<RustChannel>("channel")?.let { channel = it }
        element.readBool("requiredFeatures")?.let { requiredFeatures = it }
        element.readBool("allFeatures")?.let { allFeatures = it }
        element.readBool("emulateTerminal")?.let { emulateTerminal = it }
        element.readBool("withSudo")?.let { withSudo = it }
        element.readEnum<BacktraceMode>("backtrace")?.let { backtrace = it }
        env = EnvironmentVariablesData.readExternal(element)
        element.readBool("isRedirectInput")?.let { isRedirectInput = it }
        element.readString("redirectInputPath")?.let { redirectInputPath = it }
    }

    fun setFromCmd(cmd: CargoCommandLine) {
        channel = cmd.channel
        command = ParametersListUtil.join(listOfNotNull(cmd.toolchain, cmd.command, *cmd.additionalArguments.toTypedArray()))
        requiredFeatures = cmd.requiredFeatures
        allFeatures = cmd.allFeatures
        emulateTerminal = cmd.emulateTerminal
        withSudo = cmd.withSudo
        backtrace = cmd.backtraceMode
        workingDirectory = cmd.workingDirectory
        env = cmd.environmentVariables
        isRedirectInput = cmd.redirectInputFrom != null
        redirectInputPath = cmd.redirectInputFrom?.path
    }

    fun canBeFrom(cmd: CargoCommandLine): Boolean =
        command == ParametersListUtil.join(listOfNotNull(cmd.toolchain, cmd.command, *cmd.additionalArguments.toTypedArray()))

    @Throws(RuntimeConfigurationException::class)
    override fun checkConfiguration() {
        if (isRedirectInput) {
            val file = redirectInputFile
            when {
                file?.exists() != true -> throw RuntimeConfigurationWarning("Input file doesn't exist")
                !file.isFile -> throw RuntimeConfigurationWarning("Input file is not valid")
            }
        }

        val config = clean()
        if (config is CleanConfiguration.Err) throw config.error
        config as CleanConfiguration.Ok

        // TODO: remove when `com.intellij.execution.process.ElevationService` supports error stream redirection
        // https://github.com/intellij-rust/intellij-rust/issues/7320
        if (withSudo && showTestToolWindow(config.cmd)) {
            val message = if (SystemInfo.isWindows) {
                RsBundle.message("notification.run.tests.as.root.windows")
            } else {
                RsBundle.message("notification.run.tests.as.root.unix")
            }
            throw RuntimeConfigurationWarning(message)
        }
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        CargoCommandConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        val config = clean().ok ?: return null
        return if (showTestToolWindow(config.cmd)) {
            CargoTestRunState(environment, this, config)
        } else {
            CargoRunState(environment, this, config)
        }
    }

    private fun showTestToolWindow(commandLine: CargoCommandLine): Boolean =
        commandLine.command == "test" &&
            isFeatureEnabled(RsExperiments.TEST_TOOL_WINDOW) &&
            !Cargo.TEST_NOCAPTURE_ENABLED_KEY.asBoolean() &&
            "--nocapture" !in commandLine.additionalArguments


    override fun createTestConsoleProperties(executor: Executor): SMTRunnerConsoleProperties? {
        val config = clean().ok ?: return null
        return if (showTestToolWindow(config.cmd)) {
            CargoTestConsoleProperties(this, executor)
        } else {
            null
        }
    }

    sealed class CleanConfiguration {
        class Ok(
            val cmd: CargoCommandLine,
            val toolchain: RsToolchainBase
        ) : CleanConfiguration()

        class Err(val error: RuntimeConfigurationError) : CleanConfiguration()

        val ok: Ok? get() = this as? Ok

        companion object {
            fun error(@Suppress("UnstableApiUsage") @DialogMessage message: String) = Err(RuntimeConfigurationError(message))
        }
    }

    fun clean(): CleanConfiguration {
        val workingDirectory = workingDirectory
            ?: return CleanConfiguration.error("No working directory specified")

        val cmd = run {
            val parsed = ParsedCommand.parse(command)
                ?: return CleanConfiguration.error("No command specified")

            CargoCommandLine(
                parsed.command,
                workingDirectory,
                parsed.additionalArguments,
                redirectInputFile,
                backtrace,
                parsed.toolchain,
                channel,
                env,
                requiredFeatures,
                allFeatures,
                emulateTerminal,
                withSudo
            )
        }

        val toolchain = project.toolchain
            ?: return CleanConfiguration.error("No Rust toolchain specified")

        if (!toolchain.looksLikeValidToolchain()) {
            return CleanConfiguration.error("Invalid toolchain: ${toolchain.presentableLocation}")
        }

        if (!toolchain.isRustupAvailable && channel != RustChannel.DEFAULT) {
            return CleanConfiguration.error("Channel '$channel' is set explicitly with no rustup available")
        }

        return CleanConfiguration.Ok(cmd, toolchain)
    }

    companion object {
        fun findCargoProject(project: Project, additionalArgs: List<String>, workingDirectory: Path?): CargoProject? {
            val cargoProjects = project.cargoProjects
            cargoProjects.allProjects.singleOrNull()?.let { return it }

            val manifestPath = run {
                val idx = additionalArgs.indexOf("--manifest-path")
                if (idx == -1) return@run null
                additionalArgs.getOrNull(idx + 1)?.let { Paths.get(it) }
            }

            for (dir in listOfNotNull(manifestPath?.parent, workingDirectory)) {
                LocalFileSystem.getInstance().findFileByIoFile(dir.toFile())
                    ?.let { cargoProjects.findProjectForFile(it) }
                    ?.let { return it }
            }
            return null
        }

        fun findCargoProject(project: Project, cmd: String, workingDirectory: Path?): CargoProject? = findCargoProject(
            project, ParametersListUtil.parse(cmd), workingDirectory
        )

        fun findCargoPackage(
            cargoProject: CargoProject,
            additionalArgs: List<String>,
            workingDirectory: Path?
        ): CargoWorkspace.Package? {
            val packages = cargoProject.workspace?.packages
                ?.filter { it.origin == PackageOrigin.WORKSPACE }
                .orEmpty()

            packages.singleOrNull()?.let { return it }

            val packageName = run {
                val idx = additionalArgs.indexOf("--package")
                if (idx == -1) return@run null
                additionalArgs.getOrNull(idx + 1)
            }

            if (packageName != null) {
                return packages.find { it.name == packageName }
            }

            return packages.find { it.rootDirectory == workingDirectory }
        }

        fun findCargoTargets(
            cargoPackage: CargoWorkspace.Package,
            additionalArgs: List<String>
        ): List<CargoWorkspace.Target> {

            fun hasTarget(option: String, name: String): Boolean {
                if ("$option=$name" in additionalArgs) return true
                return additionalArgs.windowed(2).any { pair ->
                    pair.first() == option && pair.last() == name
                }
            }

            return cargoPackage.targets.filter { target ->
                when (target.kind) {
                    CargoWorkspace.TargetKind.Bin -> hasTarget("--bin", target.name)
                    CargoWorkspace.TargetKind.Test -> hasTarget("--test", target.name)
                    CargoWorkspace.TargetKind.ExampleBin,
                    is CargoWorkspace.TargetKind.ExampleLib -> hasTarget("--example", target.name)
                    CargoWorkspace.TargetKind.Bench -> hasTarget("--bench", target.name)
                    is CargoWorkspace.TargetKind.Lib -> "--lib" in additionalArgs
                    else -> false
                }
            }
        }
    }
}

val CargoProject.workingDirectory: Path get() = manifest.parent

data class ParsedCommand(val command: String, val toolchain: String?, val additionalArguments: List<String>) {
    companion object {
        fun parse(rawCommand: String): ParsedCommand? {
            val args = ParametersListUtil.parse(rawCommand)
            val command = args.firstOrNull { !it.startsWith("+") } ?: return null
            val toolchain = args.firstOrNull()?.takeIf { it.startsWith("+") }
            val additionalArguments = args.drop(args.indexOf(command) + 1)
            return ParsedCommand(command, toolchain, additionalArguments)
        }
    }
}
