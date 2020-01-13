/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.model.CargoProjectChooserDescriptor.withFileFilter
import org.rust.cargo.project.toolwindow.CargoToolWindow
import org.rust.cargo.toolchain.RustToolchain.Companion.CARGO_TOML
import org.rust.openapiext.pathAsPath
import org.rust.openapiext.saveAllDocuments
import java.nio.file.Path

class AttachCargoProjectAction : CargoProjectActionBase() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        saveAllDocuments()

        val file = when (e.place) {
            CargoToolWindow.CARGO_TOOLBAR_PLACE -> {
                val chooser = FileChooserFactory.getInstance().createFileChooser(CargoProjectChooserDescriptor, project, null)
                chooser.choose(project).singleOrNull()
            }
            else -> e.getData(PlatformDataKeys.VIRTUAL_FILE)
        } ?: return

        val cargoToml = file.findCargoToml() ?: return

        if (!project.cargoProjects.attachCargoProject(cargoToml.pathAsPath)) {
            Messages.showErrorDialog(
                project,
                "This Cargo package is already a part of an attached workspace.",
                "Unable to attach Cargo project"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        e.presentation.isEnabledAndVisible = isActionEnabled(e, project)
    }

    private fun isActionEnabled(e: AnActionEvent, project: Project): Boolean {
        return when (e.place) {
            CargoToolWindow.CARGO_TOOLBAR_PLACE -> true
            else -> {
                // We need to use `ProjectFileIndex` to check if `Cargo.toml` is in project content
                // so disable the action in dumb mode
                if (DumbService.isDumb(project)) return false
                val file = e.getData(PlatformDataKeys.VIRTUAL_FILE)
                val cargoToml = file?.findCargoToml() ?: return false

                if (!ProjectFileIndex.getInstance(project).isInContent(cargoToml)) return false

                val path = cargoToml.pathAsPath

                // Project module already contains Cargo project with `cargoToml` as manifest file
                if (project.cargoProjects.allProjects.any { it.manifest == path }) return false
                // Project module already contains a package with `cargoToml` as manifest file
                if (project.cargoProjects.allProjects.any { it.containsWorkspaceManifest(path) }) return false
                true
            }
        }
    }

    private fun CargoProject.containsWorkspaceManifest(path: Path): Boolean {
        val rootDir = path.parent
        return workspace?.packages.orEmpty().any { it.rootDirectory == rootDir }
    }

    private fun VirtualFile.findCargoToml(): VirtualFile? {
        return if (isDirectory) findChild(CARGO_TOML) else takeIf { it.isCargoToml }
    }
}

object CargoProjectChooserDescriptor : FileChooserDescriptor(true, true, false, false, false, false) {

    init {
        // The filter is not used for directories
        withFileFilter { it.isCargoToml }
        withTitle("Select Cargo.toml")
    }

    override fun isFileSelectable(file: VirtualFile): Boolean {
        return super.isFileSelectable(file) && (!file.isDirectory || file.findChild(CARGO_TOML) != null)
    }
}

private val VirtualFile.isCargoToml: Boolean get() = name == CARGO_TOML
