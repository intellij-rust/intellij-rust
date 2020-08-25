/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.ui

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.text.nullize
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.util.RsCommandLineEditor
import java.nio.file.Path
import java.nio.file.Paths

abstract class RsCommandConfigurationEditor<T : LocatableConfigurationBase<RunProfileState>>(
    protected val project: Project
) : SettingsEditor<T>() {

    abstract val command: RsCommandLineEditor

    protected fun currentWorkspace(): CargoWorkspace? =
        CargoCommandConfiguration.findCargoProject(project, command.text, currentWorkingDirectory)?.workspace

    protected val currentWorkingDirectory: Path?
        get() = workingDirectory.component.text.nullize()?.let { Paths.get(it) }

    protected val workingDirectory: LabeledComponent<TextFieldWithBrowseButton> =
        WorkingDirectoryComponent()
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
