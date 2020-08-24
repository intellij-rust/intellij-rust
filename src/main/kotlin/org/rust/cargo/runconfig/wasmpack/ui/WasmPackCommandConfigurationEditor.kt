/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.wasmpack.ui

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.layout.panel
import com.intellij.util.text.nullize
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.ui.WorkingDirectoryComponent
import org.rust.cargo.runconfig.wasmpack.WasmPackCommandConfiguration
import org.rust.cargo.runconfig.wasmpack.util.WasmPackCommandCompletionProvider
import org.rust.cargo.util.RsCommandLineEditor
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent

class WasmPackCommandConfigurationEditor(val project: Project) : SettingsEditor<WasmPackCommandConfiguration>() {
    private fun currentWorkspace(): CargoWorkspace? =
        CargoCommandConfiguration.findCargoProject(project, command.text, currentWorkingDirectory)?.workspace

    private val command: RsCommandLineEditor = RsCommandLineEditor(
        project, WasmPackCommandCompletionProvider(project.cargoProjects) { currentWorkspace() }
    )

    private val currentWorkingDirectory: Path? get() = workingDirectory.component.text.nullize()?.let { Paths.get(it) }
    private val workingDirectory: LabeledComponent<TextFieldWithBrowseButton> = WorkingDirectoryComponent()

    override fun resetEditorFrom(configuration: WasmPackCommandConfiguration) {
        command.text = configuration.command
        workingDirectory.component.text = configuration.workingDirectory?.toString().orEmpty()
    }

    override fun createEditor(): JComponent = panel {
        row("Command:") {
            command(growX, pushX)
        }

        row(workingDirectory.label) {
            workingDirectory(growX)
        }
    }

    override fun applyEditorTo(configuration: WasmPackCommandConfiguration) {
        configuration.command = command.text
        configuration.workingDirectory = currentWorkingDirectory
    }
}
