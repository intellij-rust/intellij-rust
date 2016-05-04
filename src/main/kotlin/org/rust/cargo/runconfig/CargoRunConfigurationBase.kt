package org.rust.cargo.runconfig

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.util.modules

/**
 * Base class for cargo run configurations.
 */
abstract class CargoRunConfigurationBase(name: String,
                                         runConfigurationModule: CargoRunConfigurationModule,
                                         configurationType: ConfigurationTypeBase)
: ModuleBasedConfiguration<CargoRunConfigurationModule>(name,
                                                        runConfigurationModule,
                                                        configurationType.configurationFactories[0]) {

    // The command to be run by cargo
    open var command: String = "run"

    // Arguments required by the run configuration
    open var arguments: String = ""

    // Arguments specified by the user
    open var additionalArguments: String = ""

    // Environment variables
    open var environmentVariables: Map<String, String> = mutableMapOf()

    init {
        configurationModule.module = project.modules.firstOrNull()
    }

    override fun getValidModules(): Collection<Module> = project.modules

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

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        val module = configurationModule.module ?: return null
        val toolchain = module.toolchain ?: return null
        val moduleDirectory = module.cargoProjectRoot ?: return null
        val args = ParametersListUtil.parse(arguments + additionalArguments)
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
