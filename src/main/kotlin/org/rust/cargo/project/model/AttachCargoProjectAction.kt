/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.ui.Messages
import org.rust.cargo.toolchain.RustToolchain
import org.rust.openapiext.pathAsPath


class AttachCargoProjectAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
            .withFileFilter { it.name == RustToolchain.CARGO_TOML }
            .withTitle("Select Cargo.toml")
        val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
        val file = chooser.choose(project).singleOrNull() ?: return
        if (!project.cargoProjects.attachCargoProject(file.pathAsPath)) {
            Messages.showErrorDialog(
                project,
                "This Cargo package is already a part of an attached workspace.",
                "Unable to attach Cargo project"
            )
        }
    }
}
