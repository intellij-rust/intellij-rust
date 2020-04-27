/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.refactoring.RsBaseEditorRefactoringAction

abstract class RsRefactoringAdaptorIntention : BaseElementAtCaretIntentionAction() {

    abstract val refactoringAction: RsBaseEditorRefactoringAction

    //refactorings start its own write action
    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
        refactoringAction.isAvailableOnElementInEditorAndFile(element, editor, element.containingFile, DataContext.EMPTY_CONTEXT)

    override fun invoke(project: Project, editor: Editor, element: PsiElement) =
        refactoringAction
            .getHandler(DataContext.EMPTY_CONTEXT)
            .invoke(project, editor, element.containingFile, DataContext.EMPTY_CONTEXT)
}
