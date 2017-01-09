package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockExprElement
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.util.getNextNonCommentSibling
import org.rust.lang.core.psi.util.parentOfType

class UnwrapSingleExprIntention : RustElementBaseIntentionAction() {
    override fun getText() = "Remove braces from single expression"
    override fun getFamilyName() = text

    override fun invokeImpl(project: Project, editor: Editor, element: PsiElement) {
        val ctx = findContext(element) ?: return
        val block = ctx.blockExpr
        val blockBody = ctx.blockExpr.block?.expr ?: return
        val relativeCaretPosition = editor.caretModel.offset - blockBody.textOffset

        val offset = (block.replace(blockBody) as RustExprElement).textOffset
        editor.caretModel.moveToOffset(offset + relativeCaretPosition)
    }

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
        findContext(element) != null

    private data class Context(
        val blockExpr: RustBlockExprElement
    )

    private fun findContext(element: PsiElement): Context? {
        val blockExpr = element.parentOfType<RustBlockExprElement>() ?: return null
        val block = blockExpr.block ?: return null

        return if (block.expr != null && block.lbrace.getNextNonCommentSibling() == block.expr)
            Context(blockExpr)
        else
            null
    }
}
