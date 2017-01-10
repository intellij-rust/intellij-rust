package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockExprElement
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.util.getNextNonCommentSibling
import org.rust.lang.core.psi.util.parentOfType

class UnwrapSingleExprIntention : RustElementBaseIntentionAction<RustBlockExprElement>() {
    override fun getText() = "Remove braces from single expression"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RustBlockExprElement? {
        val blockExpr = element.parentOfType<RustBlockExprElement>() ?: return null
        val block = blockExpr.block ?: return null

        return if (block.expr != null && block.lbrace.getNextNonCommentSibling() == block.expr)
            blockExpr
        else
            null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RustBlockExprElement) {
        val blockBody = ctx.block?.expr ?: return
        val relativeCaretPosition = editor.caretModel.offset - blockBody.textOffset

        val offset = (ctx.replace(blockBody) as RustExprElement).textOffset
        editor.caretModel.moveToOffset(offset + relativeCaretPosition)
    }
}
