/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.layout.panel
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.runconfig.wasmpack.WasmPackCommandConfiguration
import org.rust.cargo.runconfig.wasmpack.util.WasmPackCommandCompletionProvider
import org.rust.cargo.util.RsCommandLineEditor
import javax.swing.JComponent

class WasmPackCommandConfigurationEditor(project: Project)
    : RsCommandConfigurationEditor<WasmPackCommandConfiguration>(project) {

    override val command = RsCommandLineEditor(
        project, WasmPackCommandCompletionProvider(project.cargoProjects) { currentWorkspace() }
    )

    override fun resetEditorFrom(configuration: WasmPackCommandConfiguration) {
        super.resetEditorFrom(configuration)
        command.text = configuration.command
        workingDirectory.component.text = configuration.workingDirectory?.toString().orEmpty()
    }

    override fun applyEditorTo(configuration: WasmPackCommandConfiguration) {
        super.applyEditorTo(configuration)
        configuration.command = command.text
        configuration.workingDirectory = currentWorkingDirectory
    }

    override fun createEditor(): JComponent = panel {
        row("Command:") {
            command(growX, pushX)
        }
        row(sdkList.label) { sdkList.apply { makeWide() }() }
        row(workingDirectory.label) {
            workingDirectory(growX)
        }
    }
}
