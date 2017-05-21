package org.rust.cargo.runconfig.ui

import com.intellij.application.options.ModulesComboBox
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.Label
import com.intellij.ui.layout.*
import com.intellij.util.execution.ParametersListUtil
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RustChannel
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField


class CargoRunConfigurationEditorForm : SettingsEditor<CargoCommandConfiguration>() {

    private val comboModules = ModulesComboBox()
    private val command = JTextField()
    private val additionalArguments = RawCommandLineEditor()
    private val backtraceMode = ComboBox<BacktraceMode>().apply {
        BacktraceMode.values()
            .sortedBy { it.index }
            .forEach { addItem(it) }
    }
    private val channelLabel = Label("C&hannel:")
    private val channel = ComboBox<RustChannel>().apply {
        RustChannel.values()
            .sortedBy { it.index }
            .forEach { addItem(it) }
    }
    private val environmentVariables = EnvironmentVariablesComponent()
    private val nocapture = CheckBox("Show stdout/stderr in tests", true)


    override fun resetEditorFrom(configuration: CargoCommandConfiguration) {
        comboModules.setModules(configuration.validModules)
        comboModules.selectedModule = configuration.configurationModule.module

        configuration.cargoCommandLine.let { args ->
            command.text = args.command
            additionalArguments.text = ParametersListUtil.join(args.additionalArguments)
            backtraceMode.selectedIndex = args.backtraceMode.index
            channel.selectedIndex = args.channel.index
            environmentVariables.envs = args.environmentVariables
            nocapture.isSelected = args.nocapture
        }
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: CargoCommandConfiguration) {
        val rustupAvailable = comboModules.selectedModule?.project?.toolchain?.isRustupAvailable ?: false
        val configChannel = RustChannel.fromIndex(channel.selectedIndex)
        configuration.setModule(comboModules.selectedModule)
        configuration.cargoCommandLine = CargoCommandLine(
            command.text,
            ParametersListUtil.parse(additionalArguments.text),
            BacktraceMode.fromIndex(backtraceMode.selectedIndex),
            configChannel,
            environmentVariables.envs,
            nocapture.isSelected
        )
        channel.isEnabled = rustupAvailable || configChannel != RustChannel.DEFAULT
        if (!rustupAvailable && configChannel != RustChannel.DEFAULT) {
            throw ConfigurationException("Channel cannot be set explicitly because rustup is not avaliable")
        }
    }

    override fun createEditor(): JComponent = panel {
        labeledRow("Rust &project:", comboModules) { comboModules(CCFlags.push) }
        labeledRow("&Command:", command) {
            command(growPolicy = GrowPolicy.SHORT_TEXT)
            channelLabel.labelFor = channel
            channelLabel()
            channel()
        }

        labeledRow("&Additional arguments:", additionalArguments) {
            additionalArguments.apply {
                dialogCaption = "Additional arguments"
                makeWide()
            }()
        }
        row { nocapture() }

        row(environmentVariables.label) { environmentVariables.apply { makeWide() }() }
        labeledRow("Back&trace:", backtraceMode) { backtraceMode() }
    }

    private fun LayoutBuilder.labeledRow(labelText: String, component: JComponent, init: Row.() -> Unit) {
        val label = Label(labelText)
        label.labelFor = component
        row(label) { init() }
    }

    private fun JPanel.makeWide() {
        preferredSize = Dimension(1000, height)
    }
}
