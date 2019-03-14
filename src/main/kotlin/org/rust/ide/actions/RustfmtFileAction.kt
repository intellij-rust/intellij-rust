/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.execution.ExecutionException
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup.Companion.checkNeedInstallRustfmt
import org.rust.lang.core.psi.isRustFile
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.saveAllDocuments

class RustfmtFileAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = getContext(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val (project, toolchain, file) = getContext(e) ?: return

        val cargoProject = project.cargoProjects.findProjectForFile(file) ?: return
        if (checkNeedInstallRustfmt(cargoProject.project, cargoProject.workingDirectory)) return

        saveAllDocuments()

        val rustfmt = toolchain.rustfmt()
        try {
            project.computeWithCancelableProgress("Reformatting File with Rustfmt...") {
                rustfmt.reformatFile(cargoProject, file)
            }
            // We want to refresh file synchronously only in unit test
            // to get new text right after `reformatFile` call
            VfsUtil.markDirtyAndRefresh(!isUnitTestMode, true, true, file)
        } catch (e: ExecutionException) {
            // Just easy way to know that something wrong happened
            if (isUnitTestMode) throw e
        }
    }

    private fun getContext(e: AnActionEvent): Triple<Project, RustToolchain, VirtualFile>? {
        val project = e.project ?: return null
        val toolchain = project.toolchain ?: return null
        // Event data context doesn't contain the current virtual file
        // if action is called from toolwindow.
        // So let's try to find it manually
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: getSelectedFile(project) ?: return null
        if (!(file.isInLocalFileSystem && file.isRustFile)) return null
        return Triple(project, toolchain, file)
    }

    private fun getSelectedFile(project: Project): VirtualFile? =
        FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
}
