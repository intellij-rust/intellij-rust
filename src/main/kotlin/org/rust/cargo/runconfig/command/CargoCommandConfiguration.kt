package org.rust.cargo.runconfig.command

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import org.jdom.Element
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.CargoRunState
import org.rust.cargo.runconfig.RsRunConfigurationModule
import org.rust.cargo.runconfig.ui.CargoRunConfigurationEditorForm
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.util.modulesWithCargoProject

class CargoCommandConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : ModuleBasedConfiguration<RsRunConfigurationModule>(name, RsRunConfigurationModule(project), factory),
    RunConfigurationWithSuppressedDefaultDebugAction {

    @get: com.intellij.util.xmlb.annotations.Transient
    @set: com.intellij.util.xmlb.annotations.Transient
    var cargoCommandLine: CargoCommandLine
        get() = CargoCommandLine(_cargoArgs.command, _cargoArgs.additionalArguments, BacktraceMode.fromIndex(_cargoArgs.backtraceMode), _cargoArgs.environmentVariables)
        set(value) = with(value) {
            _cargoArgs.command = command
            _cargoArgs.additionalArguments = additionalArguments
            _cargoArgs.backtraceMode = backtraceMode.index
            _cargoArgs.environmentVariables = environmentVariables
        }

    @Property(surroundWithTag = false)
    private var _cargoArgs = SerializableCargoCommandLine()

    init {
        cargoCommandLine = CargoCommandLine(CargoConstants.Commands.RUN)
        configurationModule.module = project.modulesWithCargoProject.firstOrNull()
    }

    override fun getValidModules(): Collection<Module> = project.modulesWithCargoProject

    @Throws(RuntimeConfigurationError::class)
    override fun checkConfiguration() {
        val config = cleanConfiguration()
        if (config is ConfigurationResult.Err) throw config.error
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        CargoRunConfigurationEditorForm()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        val config = cleanConfiguration() as? ConfigurationResult.Ok ?: return null
        return CargoRunState(environment, config.toolchain, config.module, config.cargoProjectDirectory, cargoCommandLine)
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        XmlSerializer.serializeInto(this, element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        XmlSerializer.deserializeInto(this, element)
    }

    private sealed class ConfigurationResult {
        class Ok(
            val toolchain: RustToolchain,
            val module: Module,
            val cargoProjectDirectory: VirtualFile
        ) : ConfigurationResult()

        class Err(val error: RuntimeConfigurationError) : ConfigurationResult()

        companion object {
            fun error(message: String) = Err(RuntimeConfigurationError(message))
        }
    }

    private fun cleanConfiguration(): ConfigurationResult {
        val module = configurationModule.module
            ?: return ConfigurationResult.error(ExecutionBundle.message("module.not.specified.error.text"))

        val toolchain = project.toolchain
            ?: return ConfigurationResult.error("No Rust toolchain specified")

        if (!toolchain.looksLikeValidToolchain()) {
            return ConfigurationResult.error("Invalid toolchain: ${toolchain.presentableLocation}")
        }

        return ConfigurationResult.Ok(
            toolchain,
            module,
            module.cargoProjectRoot
                ?: return ConfigurationResult.error("No Cargo.toml at the root of the module")
        )
    }
}

@Tag(value = "parameters")
data class SerializableCargoCommandLine(
    var command: String = "",
    var additionalArguments: List<String> = mutableListOf(),
    var backtraceMode: Int = BacktraceMode.DEFAULT.index,
    var environmentVariables: Map<String, String> = mutableMapOf()
)
