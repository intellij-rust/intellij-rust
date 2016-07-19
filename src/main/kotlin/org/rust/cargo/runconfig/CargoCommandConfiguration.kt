package org.rust.cargo.runconfig

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.forms.CargoRunConfigurationEditorForm
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.util.modules
import org.rust.cargo.util.modulesWithCargoProject

class CargoCommandConfiguration(
    project: Project,
    name: String,
    configurationType: CargoCommandRunConfigurationType
) : ModuleBasedConfiguration<RustRunConfigurationModule>(name, RustRunConfigurationModule(project), configurationType.configurationFactories[0]) {

    var command: String = CargoConstants.Commands.RUN
    var additionalArguments: String = ""
    var environmentVariables: Map<String, String> = mutableMapOf()
    var printBacktrace: Boolean = false

    init {
        configurationModule.module = project.modulesWithCargoProject.firstOrNull()
    }

    override fun getValidModules(): Collection<Module> = project.modules

    @Throws(RuntimeConfigurationError::class)
    override fun checkConfiguration() {
        val config = getConfiguration()
        if (config is ConfigurationResult.Err) throw config.error
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        CargoRunConfigurationEditorForm()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        val config = getConfiguration()
        if (config !is ConfigurationResult.Ok) return null

        val args = ParametersListUtil.parse(additionalArguments)

        val environmentVariables = if (printBacktrace)
            environmentVariables.plus(Pair("RUST_BACKTRACE", "1"))
        else
            environmentVariables

        return CargoRunState(environment, config.toolchain, config.moduleDirectory, command, args, environmentVariables)
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
        class Ok(val toolchain: RustToolchain, val moduleDirectory: VirtualFile) : ConfigurationResult()
        class Err(val error: RuntimeConfigurationError) : ConfigurationResult()

        companion object {
            fun error(message: String) = ConfigurationResult.Err(RuntimeConfigurationError(message))
        }
    }

    @Throws(RuntimeConfigurationError::class)
    private fun getConfiguration(): ConfigurationResult {
        val module = configurationModule.module
            ?: return ConfigurationResult.error(ExecutionBundle.message("module.not.specified.error.text"))
        return ConfigurationResult.Ok(
            project.toolchain ?: return ConfigurationResult.error("No Rust toolchain specified"),

            module.cargoProjectRoot ?: return ConfigurationResult.error("No Cargo.toml at the root of the module")
        )
    }
}

class RustRunConfigurationModule(project: Project) : RunConfigurationModule(project)
