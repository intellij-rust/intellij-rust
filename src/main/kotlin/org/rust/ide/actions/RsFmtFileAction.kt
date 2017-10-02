/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.PathUtil
import org.rust.cargo.project.settings.toolchain
import org.rust.ide.notifications.showBalloon
import org.rust.lang.core.psi.isRustFile
import java.nio.file.Paths

class RsFmtFileAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
        super.update(e)
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)

        e.presentation.isEnabled = project != null && file != null && file.isInLocalFileSystem && file.isRustFile && editor != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE)

        file.canonicalPath ?: return

        val document = FileDocumentManager.getInstance().getDocument(file)
        if (document != null) {
            FileDocumentManager.getInstance().saveDocument(document)
        } else {
            FileDocumentManager.getInstance().saveAllDocuments()
        }

        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return
        val moduleDirectory = Paths.get(module.moduleFilePath).parent!!
        val cargo = module.project.toolchain?.cargo(moduleDirectory) ?: return
        try {
            cargo.reformatFile(module, file)
        } catch (e: ExecutionException) {
            // #1131 - Check if we get a `no such subcommand: fmt` and let the user know to install fmt
            if ("no such subcommand: fmt" in e.message) {
                project.showBalloon("Install rustfmt: https://github.com/rust-lang-nursery/rustfmt", NotificationType.ERROR)
            } else {
                project.showBalloon(e.message ?: "", NotificationType.ERROR)
            }
        }
    }
}
