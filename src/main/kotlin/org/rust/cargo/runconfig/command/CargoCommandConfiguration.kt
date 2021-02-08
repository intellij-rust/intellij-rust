/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.Executor
import com.intellij.execution.InputRedirectAware
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.execution.ParametersListUtil
import org.jdom.Element
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.*
import org.rust.cargo.runconfig.buildtool.CargoBuildTaskProvider
import org.rust.cargo.runconfig.ui.CargoCommandConfigurationEditor
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.tools.isRustupAvailable
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.run

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
    InputRedirectAware.InputRedirectOptions {
    override var command: String = "run"
    var channel: RustChannel = RustChannel.DEFAULT
    var requiredFeatures: Boolean = true
    var allFeatures: Boolean = false
    var emulateTerminal: Boolean = false
    var backtrace: BacktraceMode = BacktraceMode.SHORT
    var env: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

    private var isRedirectInput: Boolean = false
    private var redirectInputPath: String? = null

    override fun isRedirectInput(): Boolean = isRedirectInput

    override fun setRedirectInput(value: Boolean) {
        isRedirectInput = value
    }

    override fun getRedirectInputPath(): String? = redirectInputPath

    override fun setRedirectInputPath(value: String?) {
        redirectInputPath = value
    }

    override fun getBeforeRunTasks(): List<BeforeRunTask<*>> {
        val tasks = super.getBeforeRunTasks()
        return if (tasks.none { it is CargoBuildTaskProvider.BuildTask }) {
            tasks + CargoBuildTaskProvider.BuildTask()
        } else {
            tasks
        }
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeEnum("channel", channel)
        element.writeBool("requiredFeatures", requiredFeatures)
        element.writeBool("allFeatures", allFeatures)
        element.writeBool("emulateTerminal", emulateTerminal)
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
        element.readEnum<BacktraceMode>("backtrace")?.let { backtrace = it }
        env = EnvironmentVariablesData.readExternal(element)
        element.readBool("isRedirectInput")?.let { isRedirectInput = it }
        element.readString("redirectInputPath")?.let { redirectInputPath = it }
    }

    fun setFromCmd(cmd: CargoCommandLine) {
        channel = cmd.channel
        command = ParametersListUtil.join(cmd.command, *cmd.additionalArguments.toTypedArray())
        requiredFeatures = cmd.requiredFeatures
        allFeatures = cmd.allFeatures
        emulateTerminal = cmd.emulateTerminal
        backtrace = cmd.backtraceMode
        workingDirectory = cmd.workingDirectory
        env = cmd.environmentVariables
        isRedirectInput = cmd.redirectInputFrom != null
        redirectInputPath = cmd.redirectInputFrom?.path
    }

    fun canBeFrom(cmd: CargoCommandLine): Boolean =
        command == ParametersListUtil.join(cmd.command, *cmd.additionalArguments.toTypedArray())

    @Throws(RuntimeConfigurationError::class)
    override fun checkConfiguration() {
        val config = clean()
        if (config is CleanConfiguration.Err) throw config.error
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        CargoCommandConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        val config = clean().ok ?: return null
        return if (command.startsWith("test") &&
            isFeatureEnabled(RsExperiments.TEST_TOOL_WINDOW) &&
            !command.contains("--nocapture")) {
            CargoTestRunState(environment, this, config)
        } else {
            CargoRunState(environment, this, config)
        }
    }

    sealed class CleanConfiguration {
        class Ok(
            val cmd: CargoCommandLine,
            val toolchain: RsToolchain
        ) : CleanConfiguration()

        class Err(val error: RuntimeConfigurationError) : CleanConfiguration()

        val ok: Ok? get() = this as? Ok

        companion object {
            fun error(message: String) = Err(RuntimeConfigurationError(message))
        }
    }

    fun clean(): CleanConfiguration {
        val workingDirectory = workingDirectory
            ?: return CleanConfiguration.error("No working directory specified")
        val redirectInputFrom = redirectInputPath
            ?.takeIf { isRedirectInput && it.isNotBlank() }
            ?.let { File(it) }
        val cmd = run {
            val args = ParametersListUtil.parse(command)
            if (args.isEmpty()) {
                return CleanConfiguration.error("No command specified")
            }
            CargoCommandLine(
                args.first(),
                workingDirectory,
                args.drop(1),
                redirectInputFrom,
                backtrace,
                channel,
                env,
                requiredFeatures,
                allFeatures,
                emulateTerminal
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
