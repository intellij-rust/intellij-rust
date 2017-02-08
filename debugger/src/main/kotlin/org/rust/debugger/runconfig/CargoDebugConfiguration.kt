package org.rust.debugger.runconfig

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.Label
import com.intellij.ui.layout.GrowPolicy
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.Row
import com.intellij.ui.layout.panel
import com.intellij.util.xmlb.XmlSerializer
import com.jetbrains.cidr.execution.CidrCommandLineState
import com.jetbrains.cidr.execution.CidrRunProfile
import org.jdom.Element
import org.rust.cargo.runconfig.RustRunConfigurationModule
import org.rust.cargo.util.cargoProjectRoot
import org.rust.cargo.util.modulesWithCargoProject
import org.rust.ide.icons.RsIcons
import javax.swing.JComponent
import javax.swing.JTextField

class CargoDebugConfigurationType : ConfigurationTypeBase("CargoDebugConfigurationType",
    "Cargo debug",
    "Cargo debug run configuration",
    RsIcons.RUST) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                CargoDebugConfiguration(project, this, "Cargo debug")
        })
    }

}

class CargoDebugConfiguration(
    project: Project, factory: ConfigurationFactory, name: String?
) : ModuleBasedConfiguration<RustRunConfigurationModule>(name, RustRunConfigurationModule(project), factory),
    CidrRunProfile,
    RunConfigurationWithSuppressedDefaultRunAction {

    init {
        configurationModule.module = project.modulesWithCargoProject.firstOrNull()
    }

    override fun getValidModules(): Collection<Module> = project.modulesWithCargoProject

    override fun checkConfiguration() {
    }

    var binary: String = ""

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        Form()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): CidrCommandLineState? {
        val module = configurationModule.module
            ?: error("No Rust module")

        val projectDir = module.cargoProjectRoot!!.path
        val cmd = GeneralCommandLine("$projectDir/target/debug/$binary")
            // LLDB won't work without working directory
            .withWorkDirectory(projectDir)
        val launcher = RsDebugLauncher(project, cmd)
        return RsDebugRunState(environment, launcher)
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


private class Form : SettingsEditor<CargoDebugConfiguration>() {

    private val binary = JTextField()

    override fun resetEditorFrom(configuration: CargoDebugConfiguration) {
        binary.text = configuration.binary
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: CargoDebugConfiguration) {
        configuration.binary = binary.text
    }

    override fun createEditor(): JComponent = panel {
        labeledRow("&Binary name:", binary) { binary(growPolicy = GrowPolicy.SHORT_TEXT) }
    }

    private fun LayoutBuilder.labeledRow(labelText: String, component: JComponent, init: Row.() -> Unit) {
        val label = Label(labelText)
        label.labelFor = component
        row(label) { init() }
    }
}
