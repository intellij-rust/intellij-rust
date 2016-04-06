package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.file.PsiFileImplUtil
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.RustModules

class ContractModule : IntentionAction {

    override fun getFamilyName()        = "Contract module structure"
    override fun getText()              = "Contract module"
    override fun startInWriteAction()   = true

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) =
        file is RustFile
            && file.name == RustModules.MOD_RS
            && file.containingDirectory?.children?.size == 1

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        file ?: return
        val parent = file.parent ?: return
        val dst = parent.parent ?: return
        val fileName = "${parent.name}.rs"
        PsiFileImplUtil.setName(file, fileName)
        MoveFilesOrDirectoriesUtil.doMoveFile(file, dst)
        parent.delete()
    }
}
