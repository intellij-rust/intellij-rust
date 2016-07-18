package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.impl.file.PsiFileImplUtil
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import org.rust.ide.utils.runWriteAction
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.impl.RustFile

class RustExpandModuleAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.rustFile != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val file = e.rustFile ?: return
        runWriteAction {
            expandModule(file)
        }
    }

    companion object {
        private val AnActionEvent.rustFile: RustFile? get() = getData(CommonDataKeys.PSI_FILE) as? RustFile

        fun expandModule(file: RustFile) {
            check(ApplicationManager.getApplication().isWriteAccessAllowed)

            val dirName = FileUtil.getNameWithoutExtension(file.name)
            val directory = file.parent?.createSubdirectory(dirName)
            MoveFilesOrDirectoriesUtil.doMoveFile(file, directory)
            PsiFileImplUtil.setName(file, RustMod.MOD_RS)
        }
    }
}
