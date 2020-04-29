/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapiext.Testmark
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringSettings
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.refactoring.rename.RenamePsiFileProcessor
import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.RsConstants

class RsDirectoryRenameProcessor : RenamePsiFileProcessor() {

    override fun createRenameDialog(project: Project, element: PsiElement, nameSuggestionContext: PsiElement?, editor: Editor?): RenameDialog {
        return super.createRenameDialog(project, element.toDir(), nameSuggestionContext, editor)
    }

    override fun canProcessElement(element: PsiElement): Boolean {
        if (!(element is PsiDirectory || element is PsiDirectoryContainer)) return false
        // Do nothing for directories outside of cargo project.
        // Otherwise, it may affect rename provided by other languages like renaming of Java packages.
        // See https://youtrack.jetbrains.com/issue/IDEA-238958
        return element.project.cargoProjects.findProjectForFile(element.toDir().virtualFile) != null
    }

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        Testmarks.rustDirRenameHandler.hit()
        super.prepareRenaming(element, newName, allRenames)
        if (!RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY) return

        val modrs = element.toDir()
            .run {
                findFile(RsConstants.MOD_RS_FILE)
                    ?: parentDirectory?.findFile("$name.rs")
            } ?: return
        allRenames[modrs] = newName
    }

    private fun PsiElement.toDir(): PsiDirectory = (this as? PsiDirectoryContainer)?.directories?.first()
        ?: (this as PsiDirectory)

    object Testmarks {
        val rustDirRenameHandler = Testmark("rustDirRename")
    }
}
