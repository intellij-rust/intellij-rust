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
import org.rust.ide.refactoring.convertStruct.RsConvertToNamedFieldsAction
import org.rust.ide.refactoring.convertStruct.RsConvertToTupleAction

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

class ConvertToStructIntention : RsRefactoringAdaptorIntention() {
    override val refactoringAction: RsBaseEditorRefactoringAction
        get() = RsConvertToNamedFieldsAction()

    override fun getText() = "Convert to struct"
    override fun getFamilyName() = text

}

class ConvertToTupleIntention : RsRefactoringAdaptorIntention() {
    override val refactoringAction: RsBaseEditorRefactoringAction
        get() = RsConvertToTupleAction()

    override fun getText() = "Convert to tuple"
    override fun getFamilyName() = text
}
