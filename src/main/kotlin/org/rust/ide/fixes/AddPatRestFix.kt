/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsPatStruct
import org.rust.lang.core.psi.RsPatTupleStruct
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getPrevNonCommentSibling

class AddPatRestFix(element: PsiElement) : RsQuickFixBase<PsiElement>(element) {
    override fun getText() = RsBundle.message("intention.name.add")

    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val (pat, lBraceOrParen, rBraceOrParen) = when (element) {
            is RsPatStruct -> Triple(element, element.lbrace, element.rbrace)
            is RsPatTupleStruct -> Triple(element, element.lparen, element.rparen)
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
