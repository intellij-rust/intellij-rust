/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.ui

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.impl.SingleConfigurationConfigurable
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.text.nullize
import org.rust.RsBundle
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.runconfig.target.BuildTarget
import org.rust.cargo.toolchain.BacktraceMode
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.tools.isRustupAvailable
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.cargo.util.CargoCommandCompletionProvider
import org.rust.cargo.util.RsCommandLineEditor
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.fullWidthCell
import org.rust.openapiext.isFeatureEnabled
import org.rust.openapiext.pathTextField
import javax.swing.JCheckBox
import javax.swing.JComponent

class CargoCommandConfigurationEditor(project: Project)
    : RsCommandConfigurationEditor<CargoCommandConfiguration>(project) {
    private var panel: JComponent? = null

    private val isRemoteTarget: Boolean
        get() = DataManager.getInstance().getDataContext(panel).getData(SingleConfigurationConfigurable.RUN_ON_TARGET_NAME_KEY) != null

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
    private val channelLabel = Label(RsBundle.message("label.channel"))
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
    private val requiredFeatures = CheckBox(RsBundle.message("checkbox.implicitly.add.required.features.if.possible"), true)
    private val allFeatures = CheckBox(RsBundle.message("checkbox.use.all.features.in.tests"), false)
    private val withSudo = CheckBox(
        if (SystemInfo.isWindows) RsBundle.message("checkbox.run.with.administrator.privileges") else RsBundle.message("checkbox.run.with.root.privileges"),
        false
    ).apply {
        // TODO: remove when `com.intellij.execution.process.ElevationService` supports error stream redirection
        // https://github.com/intellij-rust/intellij-rust/issues/7320
        isEnabled = isFeatureEnabled(RsExperiments.BUILD_TOOL_WINDOW)
    }
    private val buildOnRemoteTarget = CheckBox(RsBundle.message("checkbox.build.on.remote.target"), true)

    override fun resetEditorFrom(configuration: CargoCommandConfiguration) {
        super.resetEditorFrom(configuration)

        channel.selectedIndex = configuration.channel.index
        requiredFeatures.isSelected = configuration.requiredFeatures
        allFeatures.isSelected = configuration.allFeatures
        withSudo.isSelected = configuration.withSudo
        buildOnRemoteTarget.isSelected = configuration.buildTarget.isRemote
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

        hideUnsupportedFieldsIfNeeded()
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: CargoCommandConfiguration) {
        super.applyEditorTo(configuration)

        val configChannel = RustChannel.fromIndex(channel.selectedIndex)

        configuration.channel = configChannel
        configuration.requiredFeatures = requiredFeatures.isSelected
        configuration.allFeatures = allFeatures.isSelected
        configuration.withSudo = withSudo.isSelected
        configuration.buildTarget = if (buildOnRemoteTarget.isSelected) BuildTarget.REMOTE else BuildTarget.LOCAL
        configuration.backtrace = BacktraceMode.fromIndex(backtraceMode.selectedIndex)
        configuration.env = environmentVariables.envData

        val toolchain = project.toolchain
        if (toolchain is RsWslToolchain && isRemoteTarget) {
            throw ConfigurationException(RsBundle.message("dialog.message.run.targets.cannot.be.used.alongside.with.wsl.toolchain"))
        }

        val rustupAvailable = toolchain?.isRustupAvailable ?: false
        channel.isEnabled = rustupAvailable || configChannel != RustChannel.DEFAULT
        if (!rustupAvailable && configChannel != RustChannel.DEFAULT) {
            throw ConfigurationException(RsBundle.message("dialog.message.channel.cannot.be.set.explicitly.because.rustup.not.available"))
        }

        configuration.isRedirectInput = isRedirectInput.isSelected
        configuration.redirectInputPath = redirectInputPath

        hideUnsupportedFieldsIfNeeded()
    }

    override fun createEditor(): JComponent = panel {
        row(RsBundle.message("command")) {
            fullWidthCell(command)
                .resizableColumn()
            channelLabel.labelFor = channel
            cell(channelLabel)
            cell(channel)
        }

        row { cell(requiredFeatures) }
        row { cell(allFeatures) }
        row { cell(emulateTerminal) }
        row { cell(withSudo) }
        row { cell(buildOnRemoteTarget) }

        row(environmentVariables.label) {
            fullWidthCell(environmentVariables)
        }
        row(workingDirectory.label) {
            fullWidthCell(workingDirectory)
                .resizableColumn()
            if (project.cargoProjects.allProjects.size > 1) {
                cell(cargoProject)
            }
        }
        row {
            layout(RowLayout.LABEL_ALIGNED)
            cell(isRedirectInput)
            fullWidthCell(redirectInput)
        }
        row(RsBundle.message("backtrace")) {
            cell(backtraceMode)
        }
    }.also { panel = it }

    private fun hideUnsupportedFieldsIfNeeded() {
        if (!ApplicationManager.getApplication().isDispatchThread) return
        buildOnRemoteTarget.isVisible = isRemoteTarget
    }
}
