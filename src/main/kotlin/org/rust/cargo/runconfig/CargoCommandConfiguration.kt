package org.rust.cargo.runconfig

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.rust.cargo.runconfig.forms.CargoRunConfigurationEditorForm
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.util.getModules

class CargoCommandConfiguration(project: Project,
                                name: String,
                                configurationType: CargoCommandRunConfigurationType)

    : ModuleBasedConfiguration<RustRunConfigurationModule>(name,
                                                           RustRunConfigurationModule(project),
                                                           configurationType.configurationFactories[0]) {

    var command: String = "run"
    var additionalArguments: String = ""
    var environmentVariables: Map<String, String> = mutableMapOf()

    init {
        configurationModule.module = project.getModules().firstOrNull()
    }

    override fun getValidModules(): Collection<Module> = project.getModules()

    override fun checkConfiguration() {
        val module = configurationModule.module
            ?: throw RuntimeConfigurationError(ExecutionBundle.message("module.not.specified.error.text"))

        if (module.cargoProjectRoot == null) {
            throw RuntimeConfigurationError("No Cargo.toml at the root of the module")
        }

        if (module.toolchain == null) {
            throw RuntimeConfigurationError("No Rust toolchain specified")
        }
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        CargoRunConfigurationEditorForm()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        val module = configurationModule.module ?: return null
        val toolchain = module.toolchain ?: return null
        val moduleDirectory = module.cargoProjectRoot ?: return null
        val args = ParametersListUtil.parse(additionalArguments)
        return CargoRunState(environment, toolchain, moduleDirectory, command, args, environmentVariables)
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        XmlSerializer.serializeInto(this, element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        XmlSerializer.deserializeInto(this, element)
    }
}

class RustRunConfigurationModule(project: Project) : RunConfigurationModule(project)
