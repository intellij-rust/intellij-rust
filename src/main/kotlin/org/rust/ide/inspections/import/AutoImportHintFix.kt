/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.HintAction
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.ext.RsElement

class AutoImportHintFix(
    element: RsElement,
    private val hint: String,
    private val multiple: Boolean
) : LocalQuickFixOnPsiElement(element), HintAction, HighPriorityAction {

    private val delegate: AutoImportFix = AutoImportFix(element)

    override fun getFamilyName(): String = delegate.familyName
    override fun getText(): String = delegate.name
    override fun startInWriteAction(): Boolean = delegate.startInWriteAction()

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = delegate.isAvailable

    override fun showHint(editor: Editor): Boolean {
        if (HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)) return false

        val message = ShowAutoImportPass.getMessage(multiple, hint)
        val element = startElement
        HintManager.getInstance().showQuestionHint(
            editor, message, element.textOffset, element.textRange.endOffset) {
            delegate.invoke(element.project)
            true
        }
        return true
    }

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        delegate.invoke(project)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        delegate.invoke(project)
    }
}
