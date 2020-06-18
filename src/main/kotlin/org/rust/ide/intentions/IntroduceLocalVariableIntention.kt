/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.refactoring.RsBaseEditorRefactoringAction
import org.rust.ide.refactoring.introduceVariable.RsIntroduceVariableHandler

class IntroduceLocalVariableIntention : RsRefactoringAdaptorIntention() {
    override fun getText(): String = "Introduce local variable"
    override fun getFamilyName(): String = text

    override val refactoringAction: RsBaseEditorRefactoringAction
        get() = RsIntroduceLocalVariableAction()
}

private class RsIntroduceLocalVariableAction : RsBaseEditorRefactoringAction() {
    override fun isAvailableOnElementInEditorAndFile(
        element: PsiElement,
        editor: Editor,
        file: PsiFile,
        context: DataContext
    ): Boolean = RsIntroduceVariableHandler.isAvailable(editor, file)

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        RsIntroduceVariableHandler().invoke(project, editor, file, dataContext ?: return)
    }
}
