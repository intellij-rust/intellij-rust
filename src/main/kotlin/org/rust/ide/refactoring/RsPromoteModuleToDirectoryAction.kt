/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

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
import org.rust.RsBundle
import org.rust.cargo.project.workspace.CargoWorkspace.TargetKind
import org.rust.lang.RsConstants
import org.rust.lang.RsLanguage
import org.rust.lang.core.crate.asNotFake
import org.rust.lang.core.psi.RsFile
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.openapiext.runWriteCommandAction

class RsPromoteModuleToDirectoryAction : BaseRefactoringAction() {
    override fun isEnabledOnElements(elements: Array<out PsiElement>): Boolean =
        elements.all { it.isPromotable }

    override fun isAvailableOnElementInEditorAndFile(
        element: PsiElement,
        editor: Editor,
        file: PsiFile,
        context: DataContext
    ): Boolean {
        return file.isPromotable
    }

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler = Handler

    override fun isAvailableInEditorOnly(): Boolean = false

    override fun isAvailableForLanguage(language: Language): Boolean = language.`is`(RsLanguage)

    private object Handler : RefactoringActionHandler {
        override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
            invoke(project, arrayOf(file), dataContext)
        }

        override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
            val files = elements.filterIsInstance<RsFile>()
            project.runWriteCommandAction(
                RsBundle.message("action.Rust.RsPromoteModuleToDirectoryAction.text"),
                "action.Rust.RsPromoteModuleToDirectoryAction",
                *files.toTypedArray(),
            ) {
                for (element in files) {
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
            val name = if (file.isCrateRoot) RsConstants.MAIN_RS_FILE else RsConstants.MOD_RS_FILE
            PsiFileImplUtil.setName(file, name)
        }
    }
}

private val PsiElement.isPromotable: Boolean
    get() {
        if (this !is RsFile) return false
        return if (isCrateRoot) {
            name != RsConstants.MAIN_RS_FILE && containingCrate.asNotFake?.kind?.isPromotable == true
        } else {
            name != RsConstants.MOD_RS_FILE
        }
    }

private val TargetKind.isPromotable: Boolean
    get() = when (this) {
        is TargetKind.Bin -> true
        is TargetKind.Test -> true
        is TargetKind.ExampleBin -> true
        is TargetKind.Bench -> true
        else -> false
    }
