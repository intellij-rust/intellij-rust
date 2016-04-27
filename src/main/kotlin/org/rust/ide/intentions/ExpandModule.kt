package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.file.PsiFileImplUtil
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.impl.rustMod
import org.rust.lang.core.psi.util.RustModules

class ExpandModule : IntentionAction {

    override fun getFamilyName()        = "Expand module structure"
    override fun getText()              = "Expand module"
    override fun startInWriteAction()   = true

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) =
        file is RustFile && !(file.rustMod?.ownsDirectory ?: false)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        file ?: return
        val dirName = FileUtil.getNameWithoutExtension(file.name)
        val directory = file.parent?.createSubdirectory(dirName)
        MoveFilesOrDirectoriesUtil.doMoveFile(file, directory)
        PsiFileImplUtil.setName(file, RustModules.MOD_RS)
    }
}
