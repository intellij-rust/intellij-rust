/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.extractEnumVariant

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.refactoring.RsBaseEditorRefactoringAction

class RsExtractEnumVariantAction : RsBaseEditorRefactoringAction() {
    override fun isAvailableOnElementInEditorAndFile(element: PsiElement, editor: Editor, file: PsiFile, context: DataContext): Boolean =
        findApplicableContext(editor, file) != null

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        val ctx = findApplicableContext(editor, file) ?: return

        val processor = RsExtractEnumVariantProcessor(project, editor, ctx)
        processor.setPreviewUsages(false)
        processor.run()
    }
}
