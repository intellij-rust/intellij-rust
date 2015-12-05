package org.rust.lang.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.file.PsiFileImplUtil
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import org.rust.lang.core.psi.impl.RustFileImpl
import org.rust.lang.core.psi.util.MOD_RS

class ExpandModule : IntentionAction {

    override fun getFamilyName() = "Expand module structure"
    override fun startInWriteAction() = true
    override fun getText() = "Expand Module"
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) =
        file is RustFileImpl && file.name != MOD_RS

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        file ?: return
        val dirName = FileUtil.getNameWithoutExtension(file.name)
        val directory = file.parent?.createSubdirectory(dirName)
        MoveFilesOrDirectoriesUtil.doMoveFile(file, directory)
        PsiFileImplUtil.setName(file, MOD_RS)
    }
}
