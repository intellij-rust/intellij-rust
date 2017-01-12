package org.rust.ide.actions

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.file.PsiFileImplUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import org.rust.ide.utils.checkWriteAccessAllowed
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsMod
import org.rust.lang.core.psi.impl.RsFile

class RsExpandModuleAction : BaseRefactoringAction() {
    override fun isEnabledOnElements(elements: Array<out PsiElement>): Boolean = elements.all { it is RsFile }

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler = Handler

    override fun isAvailableInEditorOnly(): Boolean = false

    override fun isAvailableForLanguage(language: Language): Boolean = language.`is`(RsLanguage)


    private object Handler : RefactoringActionHandler {
        override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
            if (file is RsFile) {
                runWriteAction {
                    expandModule(file)
                }
            }
        }

        override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
            runWriteAction {
                for (element in elements.filterIsInstance<RsFile>()) {
                    expandModule(element)
                }
            }
        }
    }

    companion object {
        fun expandModule(file: RsFile) {
            checkWriteAccessAllowed()

            val dirName = FileUtil.getNameWithoutExtension(file.name)
            val directory = file.containingDirectory?.createSubdirectory(dirName)
                ?: error("Can't expand file: no parent directory for $file at ${file.virtualFile.path}")
            MoveFilesOrDirectoriesUtil.doMoveFile(file, directory)
            PsiFileImplUtil.setName(file, RsMod.MOD_RS)
        }
    }
}
