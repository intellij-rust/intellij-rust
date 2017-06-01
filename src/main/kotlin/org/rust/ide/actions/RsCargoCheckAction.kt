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

class RsCargoCheckAction : DumbAwareAction() {
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
        val moduleDirectory = PathUtil.getParentPath(module.moduleFilePath)

        try {
            module.project.toolchain?.cargo(moduleDirectory)?.checkFile(module, file.path) ?: return
        } catch (e: ExecutionException) {
            project.showBalloon(e.message ?: "", NotificationType.ERROR)
        }
    }
}
