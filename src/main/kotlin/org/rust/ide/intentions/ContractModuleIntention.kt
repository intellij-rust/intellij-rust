package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.file.PsiFileImplUtil
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import org.rust.lang.core.psi.RsMod
import org.rust.lang.core.psi.impl.RsFile

class ContractModuleIntention : IntentionAction {

    override fun getFamilyName() = "Contract module structure"
    override fun getText() = "Contract module"
    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val parent = file.parent ?: return
        val dst = parent.parent ?: return
        val fileName = "${parent.name}.rs"
        PsiFileImplUtil.setName(file, fileName)
        MoveFilesOrDirectoriesUtil.doMoveFile(file, dst)
        parent.delete()
    }

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) =
        file is RsFile
            && file.name == RsMod.MOD_RS
            && file.containingDirectory?.children?.size == 1
}
