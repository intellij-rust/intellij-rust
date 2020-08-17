/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.Executor
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
import org.rust.cargo.runconfig.*
import org.rust.cargo.runconfig.buildtool.CargoBuildTaskProvider
import org.rust.cargo.runconfig.ui.CargoCommandConfigurationEditor
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.RustToolchain
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.run

/**
 * This class describes a Run Configuration.
 * It is basically a bunch of values which are persisted to .xml files inside .idea,
 * or displayed in the GUI form. It has to be mutable to satisfy various IDE's APIs.
 */
class CargoCommandConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : LocatableConfigurationBase<RunProfileState>(project, factory, name),
    RunConfigurationWithSuppressedDefaultDebugAction {

    var channel: RustChannel = RustChannel.DEFAULT
    var command: String = "run"
    var allFeatures: Boolean = false
    var emulateTerminal: Boolean = false
    var backtrace: BacktraceMode = BacktraceMode.SHORT
    var workingDirectory: Path? = project.cargoProjects.allProjects.firstOrNull()?.workingDirectory
    var env: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

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
        element.writeString("command", command)
        element.writeBool("allFeatures", allFeatures)
        element.writeBool("emulateTerminal", emulateTerminal)
        element.writeEnum("backtrace", backtrace)
        element.writePath("workingDirectory", workingDirectory)
        env.writeExternal(element)
    }

    /**
     * If you change serialization, make sure that the old variant is still
     * readable for several releases.
     */
    override fun readExternal(element: Element) {
        super.readExternal(element)
        element.readEnum<RustChannel>("channel")?.let { channel = it }
        element.readString("command")?.let { command = it }
        element.readBool("allFeatures")?.let { allFeatures = it }
        element.readBool("emulateTerminal")?.let { emulateTerminal = it }
        element.readEnum<BacktraceMode>("backtrace")?.let { backtrace = it }
        element.readPath("workingDirectory")?.let { workingDirectory = it }
        env = EnvironmentVariablesData.readExternal(element)
    }

    fun setFromCmd(cmd: CargoCommandLine) {
        channel = cmd.channel
        command = ParametersListUtil.join(cmd.command, *cmd.additionalArguments.toTypedArray())
        allFeatures = cmd.allFeatures
        emulateTerminal = cmd.emulateTerminal
        backtrace = cmd.backtraceMode
        workingDirectory = cmd.workingDirectory
        env = cmd.environmentVariables
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
            val toolchain: RustToolchain
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
        val cmd = run {
            val args = ParametersListUtil.parse(command)
            if (args.isEmpty()) {
                return CleanConfiguration.error("No command specified")
            }
            CargoCommandLine(
                args.first(),
                workingDirectory,
                args.drop(1),
                backtrace,
                channel,
                env,
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

    override fun suggestedName(): String? = command.substringBefore(' ').capitalize()

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
    }
}

val CargoProject.workingDirectory: Path get() = manifest.parent
