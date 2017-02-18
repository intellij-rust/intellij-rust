package org.rust.cargo.runconfig

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.util.modulesWithCargoProject
import com.intellij.util.xmlb.annotations.Transient as XmlbTransient

abstract class CargoConfigurationBase(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : ModuleBasedConfiguration<RsRunConfigurationModule>(name, RsRunConfigurationModule(project), factory) {

    init {
        configurationModule.module = project.modulesWithCargoProject.firstOrNull()
    }

    abstract fun getState(
        executor: Executor,
        environment: ExecutionEnvironment,
        config: ConfigurationResult
    ): RunProfileState?

    override fun getValidModules(): Collection<Module> = project.modulesWithCargoProject

    @Throws(RuntimeConfigurationError::class)
    override fun checkConfiguration() {
        cleanConfiguration() // we are only interested in eventual exceptions here
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        try {
            val config = cleanConfiguration()
            return getState(executor, environment, config)
        } catch (e: RuntimeConfigurationError) {
            return null
        }
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        XmlSerializer.serializeInto(this, element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        XmlSerializer.deserializeInto(this, element)
    }

    data class ConfigurationResult(
        val toolchain: RustToolchain,
        val module: Module,
        val cargoProjectDirectory: VirtualFile
    )

    @Throws(RuntimeConfigurationError::class)
    fun cleanConfiguration(): ConfigurationResult {
        val module = configurationModule.module
            ?: throw RuntimeConfigurationError(ExecutionBundle.message("module.not.specified.error.text"))

        val toolchain = project.toolchain
            ?: throw RuntimeConfigurationError("No Rust toolchain specified")

        if (!toolchain.looksLikeValidToolchain()) {
            throw RuntimeConfigurationError("Invalid toolchain: ${toolchain.presentableLocation}")
        }

        val cargoProjectDirectory = module.cargoProjectRoot
            ?: throw RuntimeConfigurationError("No Cargo.toml at the root of the module")

        return ConfigurationResult(toolchain, module, cargoProjectDirectory)
    }
}
