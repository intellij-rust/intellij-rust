/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.model.CargoProjectChooserDescriptor.withFileFilter
import org.rust.cargo.toolchain.RustToolchain
import org.rust.openapiext.pathAsPath
import org.rust.openapiext.saveAllDocuments


class AttachCargoProjectAction : CargoProjectActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        saveAllDocuments()
        val chooser = FileChooserFactory.getInstance().createFileChooser(CargoProjectChooserDescriptor, project, null)
        val file = chooser.choose(project).singleOrNull() ?: return
        val cargoToml = if (file.isDirectory) {
            file.findChild(RustToolchain.CARGO_TOML) ?: return
        } else {
            file
        }
        if (!project.cargoProjects.attachCargoProject(cargoToml.pathAsPath)) {
            Messages.showErrorDialog(
                project,
                "This Cargo package is already a part of an attached workspace.",
                "Unable to attach Cargo project"
            )
        }
    }
}

object CargoProjectChooserDescriptor : FileChooserDescriptor(true, true, false, false, false, false) {

    init {
        // The filter is not used for directories
        withFileFilter { it.name == RustToolchain.CARGO_TOML }
        withTitle("Select Cargo.toml")
    }

    override fun isFileSelectable(file: VirtualFile): Boolean {
        return super.isFileSelectable(file) && (!file.isDirectory || file.findChild(RustToolchain.CARGO_TOML) != null)
    }
}
