package org.rust.ide.actions

import com.intellij.execution.ExecutionException
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.PathUtil
import org.rust.cargo.commands.Cargo
import org.rust.cargo.project.pathToCargo
import org.rust.cargo.project.util.getModules

class RustFmtFileAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent?) {
        val project = e?.project ?: return
        val file = e?.getRequiredData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return
        val moduleManager = ModuleRootManager.getInstance(module)
        val pathToCargo = moduleManager.sdk?.pathToCargo ?: return
        val moduleDirectory = PathUtil.getParentPath(module.moduleFilePath)

        val component = e?.getRequiredData(CommonDataKeys.EDITOR)?.component ?: return

        try {
            Cargo(pathToCargo, moduleDirectory).reformatFile(file.path)
        } catch (e: ExecutionException) {
            ExternalSystemUiUtil.showBalloon(component, MessageType.ERROR, e.message ?: "")
        }
        VfsUtil.markDirtyAndRefresh(true, true, true, file);
    }
}
