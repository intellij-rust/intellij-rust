package org.rust.cargo.runconfig

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationModule
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.PathUtil
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.rust.cargo.project.util.getModules
import org.rust.cargo.runconfig.forms.CargoRunConfigurationEditorForm

class CargoCommandConfiguration(project: Project,
                                name: String,
                                configurationType: CargoCommandRunConfigurationType)

    : ModuleBasedConfiguration<RustRunConfigurationModule>(name,
                                                           RustRunConfigurationModule(project),
                                                           configurationType.configurationFactories[0]) {

    var command: String = "run"
    var additionalArguments: String = ""

    init {
        configurationModule.module = project.getModules().firstOrNull()
    }

    override fun getValidModules(): Collection<Module> = project.getModules()

    override fun checkConfiguration() {
        super.checkConfiguration()
        configurationModule.checkForWarning()
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        CargoRunConfigurationEditorForm()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        val module = configurationModule.module ?: return null
        val moduleManager = ModuleRootManager.getInstance(module)
        val sdk = moduleManager.sdk ?: return null
        val workDirectory = PathUtil.getParentPath(module.moduleFilePath)
        val args = ParametersListUtil.parse(additionalArguments)
        return CargoRunState(environment, sdk, workDirectory, command, args)
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        writeModule(element)
        XmlSerializer.serializeInto(this, element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        readModule(element)
        XmlSerializer.deserializeInto(this, element)
    }
}

class RustRunConfigurationModule(project: Project) : RunConfigurationModule(project)
