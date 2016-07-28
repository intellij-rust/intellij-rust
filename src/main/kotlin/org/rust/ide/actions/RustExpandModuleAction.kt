package org.rust.ide.actions

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
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
import org.rust.ide.utils.runWriteAction
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.impl.RustFile

class RustExpandModuleAction : BaseRefactoringAction() {
    override fun isEnabledOnElements(elements: Array<out PsiElement>): Boolean = elements.all { it is RustFile }

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler = Handler

    override fun isAvailableInEditorOnly(): Boolean = false

    override fun isAvailableForLanguage(language: Language): Boolean = language.`is`(RustLanguage)


    private object Handler : RefactoringActionHandler {
        override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
            if (file is RustFile) {
                runWriteAction {
                    expandModule(file)
                }
            }
        }

        override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
            runWriteAction {
                for (element in elements.filterIsInstance<RustFile>()) {
                    expandModule(element)
                }
            }
        }
    }

    companion object {
        fun expandModule(file: RustFile) {
            checkWriteAccessAllowed()

            val dirName = FileUtil.getNameWithoutExtension(file.name)
            val directory = file.parent?.createSubdirectory(dirName)
            MoveFilesOrDirectoriesUtil.doMoveFile(file, directory)
            PsiFileImplUtil.setName(file, RustMod.MOD_RS)
        }
    }
}
