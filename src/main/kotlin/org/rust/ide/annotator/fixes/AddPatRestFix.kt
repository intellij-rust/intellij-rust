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
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsPatStruct
import org.rust.lang.core.psi.RsPatTupleStruct
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getPrevNonCommentSibling

class AddPatRestFix(element: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getText() = "Add '..'"

    override fun getFamilyName() = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val (pat, lBraceOrParen, rBraceOrParen) = when (startElement) {
            is RsPatStruct -> Triple(startElement, startElement.lbrace, startElement.rbrace)
            is RsPatTupleStruct -> Triple(startElement, startElement.lparen, startElement.rparen)
            else -> return
        }
        val lastSibling = rBraceOrParen.getPrevNonCommentSibling() ?: return
        val psiFactory = RsPsiFactory(project)
        val anchor = if (lastSibling.elementType == RsElementTypes.COMMA || lastSibling == lBraceOrParen) {
            lastSibling
        } else {
            pat.addAfter(psiFactory.createComma(), lastSibling)
        }
        pat.addAfter(psiFactory.createPatRest(), anchor)
    }
}
