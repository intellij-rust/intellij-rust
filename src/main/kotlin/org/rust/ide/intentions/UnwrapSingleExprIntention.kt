/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.ext.getNextNonCommentSibling
import org.rust.lang.core.psi.ext.ancestorStrict

class UnwrapSingleExprIntention : RsElementBaseIntentionAction<RsBlockExpr>() {
    override fun getText() = "Remove braces from single expression"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsBlockExpr? {
        val blockExpr = element.ancestorStrict<RsBlockExpr>() ?: return null
        if (blockExpr.unsafe != null) return null
        val block = blockExpr.block

        return if (block.expr != null && block.lbrace.getNextNonCommentSibling() == block.expr)
            blockExpr
        else
            null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsBlockExpr) {
        val blockBody = ctx.block.expr ?: return
        val relativeCaretPosition = editor.caretModel.offset - blockBody.textOffset

        val offset = (ctx.replace(blockBody) as RsExpr).textOffset
        editor.caretModel.moveToOffset(offset + relativeCaretPosition)
    }
}
