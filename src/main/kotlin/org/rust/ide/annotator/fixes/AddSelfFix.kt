/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.valueParameters

class AddSelfFix(function: RsFunction) : LocalQuickFixAndIntentionActionOnPsiElement(function) {
    override fun getFamilyName() = "Add self to function"

    override fun getText() = "Add self to function"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val function = startElement as RsFunction
        val hasParameters = function.valueParameters.isNotEmpty()
        val psiFactory = RsPsiFactory(project)

        val valueParameterList = function.valueParameterList
        val lparen = valueParameterList?.firstChild

        val self = psiFactory.createSelf()

        valueParameterList?.addAfter(self, lparen)
        if (hasParameters) {
            // IDE error if use addAfter(comma, self)
            val parent = lparen?.parent
            parent?.addAfter(psiFactory.createComma(), parent.firstChild.nextSibling)
        }
    }
}
