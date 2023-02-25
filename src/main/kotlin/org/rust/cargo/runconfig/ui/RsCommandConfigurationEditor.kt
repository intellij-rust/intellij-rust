/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.ui

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.CheckBox
import com.intellij.util.text.nullize
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.RsCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.util.RsCommandLineEditor
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JCheckBox

abstract class RsCommandConfigurationEditor<T : RsCommandConfiguration>(
    protected val project: Project
) : SettingsEditor<T>() {

    abstract val command: RsCommandLineEditor

    protected val emulateTerminal: JCheckBox =
        CheckBox("Emulate terminal in output console", RsCommandConfiguration.emulateTerminalDefault)

    protected fun currentWorkspace(): CargoWorkspace? =
        CargoCommandConfiguration.findCargoProject(project, command.text, currentWorkingDirectory)?.workspace

    protected val currentWorkingDirectory: Path?
        get() = workingDirectory.component.text.nullize()?.let { Paths.get(it) }

    protected val workingDirectory: LabeledComponent<TextFieldWithBrowseButton> =
        WorkingDirectoryComponent()

    override fun resetEditorFrom(configuration: T) {
        command.text = configuration.command
        workingDirectory.component.text = configuration.workingDirectory?.toString().orEmpty()
        emulateTerminal.isSelected = configuration.emulateTerminal
    }

    override fun applyEditorTo(configuration: T) {
        configuration.command = command.text
        configuration.workingDirectory = currentWorkingDirectory
        configuration.emulateTerminal = emulateTerminal.isSelected
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
