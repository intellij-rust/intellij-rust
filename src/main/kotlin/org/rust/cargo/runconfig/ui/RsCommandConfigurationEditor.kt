/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.ui

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.util.text.nullize
import org.rust.cargo.project.configurable.RsConfigurableToolchainList
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.RsCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.util.RsCommandLineEditor
import org.rust.ide.sdk.RsSdkListCellRenderer
import java.awt.Dimension
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JPanel

abstract class RsCommandConfigurationEditor<T : RsCommandConfiguration>(
    protected val project: Project
) : SettingsEditor<T>() {

    abstract val command: RsCommandLineEditor

    protected fun currentWorkspace(): CargoWorkspace? =
        CargoCommandConfiguration.findCargoProject(project, command.text, currentWorkingDirectory)?.workspace

    protected val currentWorkingDirectory: Path?
        get() = workingDirectory.component.text.nullize()?.let { Paths.get(it) }

    protected val workingDirectory: LabeledComponent<TextFieldWithBrowseButton> =
        WorkingDirectoryComponent()

    private val toolchainList = RsConfigurableToolchainList.getInstance(project)

    var sdk: Sdk?
        get() = sdkList.component.selectedItem as? Sdk
        set(value) {
            val items: MutableList<Sdk?> = toolchainList.allRustSdks.toMutableList()
            items.add(0, null)
            val selection = value?.let { toolchainList.model.findSdk(it.name) }
            sdkList.component.model = CollectionComboBoxModel(items, selection)
        }

    protected val sdkList = run {
        val comboBox = ComboBox<Sdk?>().apply {
            renderer = RsSdkListCellRenderer(null, "<Project Default>")
        }
        LabeledComponent.create(comboBox, "Rust &toolchain:")
    }

    override fun disposeEditor() {
        toolchainList.disposeModel()
        super.disposeEditor()
    }

    override fun resetEditorFrom(configuration: T) {
        sdk = configuration.sdk
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: T) {
        configuration.sdk = sdk
    }

    protected fun JPanel.makeWide() {
        preferredSize = Dimension(1000, height)
    }
}

private class WorkingDirectoryComponent : LabeledComponent<TextFieldWithBrowseButton>() {
    init {
        component = TextFieldWithBrowseButton().apply {
            val fileChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
                title = ExecutionBundle.message("select.working.directory.message")
            }
            addBrowseFolderListener(null, null, null, fileChooser)
        }
        text = ExecutionBundle.message("run.configuration.working.directory.label")
    }
}
