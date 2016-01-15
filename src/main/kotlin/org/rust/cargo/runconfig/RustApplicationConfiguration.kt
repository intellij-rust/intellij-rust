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
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.rust.cargo.project.util.getModules

class RustApplicationConfiguration(project: Project,
                                   name: String,
                                   configurationType: RustApplicationRunConfigurationType)

    : ModuleBasedConfiguration<RustRunConfigurationModule>(name,
                                                           RustRunConfigurationModule(project),
                                                           configurationType.configurationFactories[0]) {

    var isRelease: Boolean = false

    override fun getValidModules(): Collection<Module> = project.getModules()

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        RustRunConfigurationEditorForm()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        RustRunState(environment, configurationModule.module!!, isRelease)

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
