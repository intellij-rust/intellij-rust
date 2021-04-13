/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.ui

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.Label
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.Row
import com.intellij.ui.layout.panel
import com.intellij.util.text.nullize
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.tools.isRustupAvailable
import org.rust.cargo.util.CargoCommandCompletionProvider
import org.rust.cargo.util.RsCommandLineEditor
import org.rust.openapiext.pathTextField
import javax.swing.JCheckBox
import javax.swing.JComponent

class CargoCommandConfigurationEditor(project: Project)
    : RsCommandConfigurationEditor<CargoCommandConfiguration>(project) {

    override val command = RsCommandLineEditor(
        project, CargoCommandCompletionProvider(project.cargoProjects) { currentWorkspace() }
    )

    private val allCargoProjects: List<CargoProject> =
        project.cargoProjects.allProjects.sortedBy { it.presentableName }

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

    private val cargoProject = ComboBox<CargoProject>().apply {
        renderer = SimpleListCellRenderer.create("") { it.presentableName }
        allCargoProjects.forEach { addItem(it) }

        addItemListener {
            setWorkingDirectoryFromSelectedProject()
        }
    }

    private val redirectInput: TextFieldWithBrowseButton =
        pathTextField(FileChooserDescriptorFactory.createSingleFileDescriptor(), this, "")
            .apply { isEnabled = false }

    private val redirectInputPath: String?
        get() = redirectInput.text.nullize()?.let { FileUtil.toSystemIndependentName(it) }

    private val isRedirectInput: JCheckBox = CheckBox(ExecutionBundle.message("redirect.input.from"), false)
        .apply { addChangeListener { redirectInput.isEnabled = isSelected } }

    private fun setWorkingDirectoryFromSelectedProject() {
        val selectedProject = run {
            val idx = cargoProject.selectedIndex
            if (idx == -1) return
            cargoProject.getItemAt(idx)
        }
        workingDirectory.component.text = selectedProject.workingDirectory.toString()
    }

    private val environmentVariables = EnvironmentVariablesComponent()
    private val requiredFeatures = CheckBox("Implicitly add required features if possible", true)
    private val allFeatures = CheckBox("Use all features in tests", false)
    private val emulateTerminal = CheckBox("Emulate terminal in output console", false)
    private val withSudo = CheckBox(
        if (SystemInfo.isWindows) "Run with Administrator privileges" else "Run with root privileges",
        false
    )

    override fun resetEditorFrom(configuration: CargoCommandConfiguration) {
        super.resetEditorFrom(configuration)

        channel.selectedIndex = configuration.channel.index
        requiredFeatures.isSelected = configuration.requiredFeatures
        allFeatures.isSelected = configuration.allFeatures
        emulateTerminal.isSelected = configuration.emulateTerminal
        withSudo.isSelected = configuration.withSudo
        backtraceMode.selectedIndex = configuration.backtrace.index
        environmentVariables.envData = configuration.env

        val vFile = currentWorkingDirectory?.let { LocalFileSystem.getInstance().findFileByIoFile(it.toFile()) }
        if (vFile == null) {
            cargoProject.selectedIndex = -1
        } else {
            val projectForWd = project.cargoProjects.findProjectForFile(vFile)
            cargoProject.selectedIndex = allCargoProjects.indexOf(projectForWd)
        }

        isRedirectInput.isSelected = configuration.isRedirectInput
        redirectInput.text = configuration.redirectInputPath ?: ""
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: CargoCommandConfiguration) {
        super.applyEditorTo(configuration)

        val configChannel = RustChannel.fromIndex(channel.selectedIndex)

        configuration.channel = configChannel
        configuration.requiredFeatures = requiredFeatures.isSelected
        configuration.allFeatures = allFeatures.isSelected
        configuration.emulateTerminal = emulateTerminal.isSelected && !SystemInfo.isWindows
        configuration.withSudo = withSudo.isSelected
        configuration.backtrace = BacktraceMode.fromIndex(backtraceMode.selectedIndex)
        configuration.env = environmentVariables.envData

        val rustupAvailable = project.toolchain?.isRustupAvailable ?: false
        channel.isEnabled = rustupAvailable || configChannel != RustChannel.DEFAULT
        if (!rustupAvailable && configChannel != RustChannel.DEFAULT) {
            throw ConfigurationException("Channel cannot be set explicitly because rustup is not available")
        }

        configuration.isRedirectInput = isRedirectInput.isSelected
        configuration.redirectInputPath = redirectInputPath
    }

    override fun createEditor(): JComponent = panel {
        labeledRow("&Command:", command) {
            command(CCFlags.pushX, CCFlags.growX)
            channelLabel.labelFor = channel
            channelLabel()
            channel()
        }

        row { requiredFeatures() }
        row { allFeatures() }

        if (!SystemInfo.isWindows) {
            row { emulateTerminal() }
        }
        row { withSudo() }

        row(environmentVariables.label) {
            environmentVariables(growX)
        }
        row(workingDirectory.label) {
            workingDirectory(growX)
            if (project.cargoProjects.allProjects.size > 1) {
                cargoProject(growX)
            }
        }
        row {
            cell(isFullWidth = true) {
                isRedirectInput()
                redirectInput()
            }
        }
        labeledRow("Back&trace:", backtraceMode) { backtraceMode() }
    }

    private fun LayoutBuilder.labeledRow(labelText: String, component: JComponent, init: Row.() -> Unit) {
        val label = Label(labelText)
        label.labelFor = component
        row(label) { init() }
    }
}
