/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.panel
import org.rust.RsBundle
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.runconfig.wasmpack.WasmPackCommandConfiguration
import org.rust.cargo.runconfig.wasmpack.util.WasmPackCommandCompletionProvider
import org.rust.cargo.util.RsCommandLineEditor
import org.rust.openapiext.fullWidthCell
import javax.swing.JComponent

class WasmPackCommandConfigurationEditor(project: Project)
    : RsCommandConfigurationEditor<WasmPackCommandConfiguration>(project) {

    override val command = RsCommandLineEditor(
        project, WasmPackCommandCompletionProvider(project.cargoProjects) { currentWorkspace() }
    )

    override fun createEditor(): JComponent = panel {
        row(RsBundle.message("command2")) {
            fullWidthCell(command)
        }

        row { cell(emulateTerminal) }

        row(workingDirectory.label) {
            fullWidthCell(workingDirectory)
        }
    }
}
