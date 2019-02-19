/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RustToolchain
import org.rust.ide.notifications.showBalloon
import org.rust.lang.core.psi.isRustFile
import org.rust.openapiext.isUnitTestMode

class RustfmtFileAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = getContext(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val (project, toolchain, file) = getContext(e) ?: return

        FileDocumentManager.getInstance().saveAllDocuments()

        val rustfmt = toolchain.rustfmt()
        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously<ProcessOutput, ExecutionException>({
                rustfmt.reformatFile(project, file)
            }, "Reformatting File with Rustfmt...", true, project)
            // We want to refresh file synchronously only in unit test
            // to get new text right after `reformatFile` call
            VfsUtil.markDirtyAndRefresh(!isUnitTestMode, true, true, file)
        } catch (e: ExecutionException) {
            // Just easy way to know that something wrong happened
            if (isUnitTestMode) throw e
            val message = e.message ?: ""

            // #1131 - Check if we get a "'rustfmt' is not installed" and let the user know to install fmt
            if ("'rustfmt' is not installed" in message) {
                val projectDir = project.cargoProjects.findProjectForFile(file)?.manifest?.parent
                val action = if (projectDir != null) InstallComponentAction(projectDir, "rustfmt-preview") else null
                project.showBalloon("Rustfmt is not installed", NotificationType.ERROR, action)
            } else {
                project.showBalloon(message, NotificationType.ERROR)
            }
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
