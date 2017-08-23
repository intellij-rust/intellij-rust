/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.ExternalizablePath
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.CargoRunState
import org.rust.cargo.runconfig.RsRunConfigurationModule
import org.rust.cargo.runconfig.ui.CargoRunConfigurationEditorForm
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.util.modulesWithCargoProject
import java.nio.file.Path
import java.nio.file.Paths

/**
 * This class describes a Run Configuration.
 * It is basically a bunch of values which are persisted to .xml files inside .idea,
 * or displayed in the GUI form. It has to be mutable to satisfy various IDE's APIs.
 */
class CargoCommandConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : ModuleBasedConfiguration<RsRunConfigurationModule>(name, RsRunConfigurationModule(project), factory),
    RunConfigurationWithSuppressedDefaultDebugAction {

    var channel: RustChannel = RustChannel.DEFAULT
    var command: String = "run"
    var nocapture: Boolean = true
    var backtrace: BacktraceMode = BacktraceMode.SHORT
    var workingDirectory: Path? = null
    var env: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

    init {
        configurationModule.module = project.modulesWithCargoProject.firstOrNull()
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeEnum("channel", channel)
        element.writeString("command", command)
        element.writeBool("nocapture", nocapture)
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
        val oldStyle = element.children.find { it.name == "parameters" }
        // BACKCOMPAT: can be removed after a couple of releases
        if (oldStyle != null) {
            data class SerializableCargoCommandLine(
                var command: String = "",
                var additionalArguments: List<String> = mutableListOf(),
                var backtraceMode: Int = BacktraceMode.DEFAULT.index,
                var channel: Int = RustChannel.DEFAULT.index,
                var environmentVariables: Map<String, String> = mutableMapOf(),
                var nocapture: Boolean = true
            )

            val cmd = SerializableCargoCommandLine()
            XmlSerializer.deserializeInto(cmd, oldStyle)
            channel = RustChannel.fromIndex(cmd.channel)
            command = ParametersListUtil.join(cmd.command, *cmd.additionalArguments.toTypedArray())
            nocapture = cmd.nocapture
            backtrace = BacktraceMode.fromIndex(cmd.backtraceMode)
            env = EnvironmentVariablesData.create(cmd.environmentVariables, true)
            return
        }

        element.readEnum<RustChannel>("channel")?.let { channel = it }
        element.readString("command")?.let { command = it }
        element.readBool("nocapture")?.let { nocapture = it }
        element.readEnum<BacktraceMode>("backtrace")?.let { backtrace = it }
        element.readPath("workingDirectory")?.let { workingDirectory = it }
        env = EnvironmentVariablesData.readExternal(element)
    }

    fun setFromCmd(cmd: CargoCommandLine) {
        channel = cmd.channel
        command = ParametersListUtil.join(cmd.command, *cmd.additionalArguments.toTypedArray())
        nocapture = cmd.nocapture
        backtrace = cmd.backtraceMode
        workingDirectory = cmd.workingDirectory
        env = cmd.environmentVariables
    }

    fun canBeFrom(cmd: CargoCommandLine): Boolean =
        command == ParametersListUtil.join(cmd.command, *cmd.additionalArguments.toTypedArray())

    override fun getValidModules(): Collection<Module> = project.modulesWithCargoProject

    @Throws(RuntimeConfigurationError::class)
    override fun checkConfiguration() {
        val config = clean()
        if (config is CleanConfiguration.Err) throw config.error
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        CargoRunConfigurationEditorForm(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? =
        clean().ok?.let { CargoRunState(environment, it) }

    sealed class CleanConfiguration {
        class Ok(
            val cmd: CargoCommandLine,
            val toolchain: RustToolchain,
            val module: Module,
            val cargoProjectDirectory: VirtualFile
        ) : CleanConfiguration()

        class Err(val error: RuntimeConfigurationError) : CleanConfiguration()

        val ok: CleanConfiguration.Ok? get() = this as? Ok

        companion object {
            fun error(message: String) = Err(RuntimeConfigurationError(message))
        }
    }

    fun clean(): CleanConfiguration {
        val cmd = run {
            val args = ParametersListUtil.parse(command)
            if (args.isEmpty()) {
                return CleanConfiguration.error("No command specified")
            }
            CargoCommandLine(args.first(), args.drop(1), backtrace, channel, workingDirectory, env, nocapture)
        }

        val module = configurationModule.module
            ?: return CleanConfiguration.error(ExecutionBundle.message("module.not.specified.error.text"))

        val toolchain = project.toolchain
            ?: return CleanConfiguration.error("No Rust toolchain specified")

        if (!toolchain.looksLikeValidToolchain()) {
            return CleanConfiguration.error("Invalid toolchain: ${toolchain.presentableLocation}")
        }


        if (!toolchain.isRustupAvailable && channel != RustChannel.DEFAULT) {
            return CleanConfiguration.error("Channel '$channel' is set explicitly with no rustup available")
        }

        return CleanConfiguration.Ok(
            cmd,
            toolchain,
            module,
            module.cargoProjectRoot
                ?: return CleanConfiguration.error("No Cargo.toml at the root of the module")
        )
    }

}


private fun Element.writeString(name: String, value: String) {
    val opt = org.jdom.Element("option")
    opt.setAttribute("name", name)
    opt.setAttribute("value", value)
    addContent(opt)
}

private fun Element.readString(name: String): String? =
    children
        .find { it.name == "option" && it.getAttributeValue("name") == name }
        ?.getAttributeValue("value")


private fun Element.writeBool(name: String, value: Boolean) {
    writeString(name, value.toString())
}

private fun Element.readBool(name: String) = readString(name)?.toBoolean()

private fun <E : Enum<*>> Element.writeEnum(name: String, value: E) {
    writeString(name, value.name)
}

private inline fun <reified E : Enum<E>> Element.readEnum(name: String): E? {
    val variantName = readString(name) ?: return null
    return try {
        java.lang.Enum.valueOf(E::class.java, variantName)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun Element.writePath(name: String, value: Path?) {
    if (value != null) {
        val s = ExternalizablePath.urlValue(value.toString())
        writeString(name, s)
    }
}

private fun Element.readPath(name: String): Path? {
    return readString(name)?.let { Paths.get(ExternalizablePath.localPathValue(it)) }
}
