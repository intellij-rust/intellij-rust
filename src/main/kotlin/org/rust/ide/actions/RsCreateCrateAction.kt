/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.RunCargoCommandActionBase
import org.rust.cargo.toolchain.RustToolchain
import org.rust.ide.actions.ui.showCargoNewCrateUI
import org.rust.openapiext.pathAsPath


class RsCreateCrateAction : RunCargoCommandActionBase(CargoIcons.ICON) {
    override fun actionPerformed(e: AnActionEvent) {
        val dataContext = e.dataContext
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
        val root = getRootFolder(dataContext) ?: return
        val toolchain = project.toolchain ?: return

        val ui = showCargoNewCrateUI(project, root)
        ui.selectCargoCrateSettings()?.let {
            createProject(project, toolchain, root, it.crateName, it.binary)
        }
    }

    private fun getRootFolder(dataContext: DataContext): VirtualFile? {
        val file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return null
        return if (!file.isDirectory) {
            file.parent
        } else file
    }

    private fun createProject(
        project: Project,
        toolchain: RustToolchain,
        root: VirtualFile,
        name: String,
        binary: Boolean
    ) {
        val cargo = toolchain.cargoOrWrapper(
            project.cargoProjects.findProjectForFile(root)?.workspaceRootDir?.pathAsPath)

        val targetDir = runWriteAction {
            root.createChildDirectory(this, name)
        }
        cargo.init(project, targetDir, binary, "none")

        val manifest = targetDir.findChild(RustToolchain.CARGO_TOML)
        manifest?.let {
            project.cargoProjects.attachCargoProject(it.pathAsPath)
        }
    }
}
