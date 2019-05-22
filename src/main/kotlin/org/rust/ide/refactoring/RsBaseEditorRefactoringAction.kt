/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction
import org.rust.lang.RsLanguage

abstract class RsBaseEditorRefactoringAction : BaseRefactoringAction() {
    override fun isAvailableInEditorOnly(): Boolean = true

    override fun isEnabledOnElements(elements: Array<out PsiElement>): Boolean = false

    override fun getHandler(dataContext: DataContext): RefactoringActionHandler = Handler()

    override fun isAvailableForLanguage(language: Language): Boolean = language.`is`(RsLanguage)

    abstract fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?)

    private inner class Handler : RefactoringActionHandler {
        override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
            this@RsBaseEditorRefactoringAction.invoke(project, editor, file, dataContext)
        }

        override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
            // never called from editor
        }
    }
}
