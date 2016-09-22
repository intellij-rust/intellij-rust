package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockExprElement
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.util.getNextNonCommentSibling
import org.rust.lang.core.psi.util.parentOfType

class UnwrapSingleExprIntention : PsiElementBaseIntentionAction() {
    override fun getText() = "Remove braces from single expression"
    override fun getFamilyName() = text
    override fun startInWriteAction() = true

    private data class Context(
        val blockExpr: RustBlockExprElement
    )

    private fun findContext(element: PsiElement): Context? {
        if (!element.isWritable) return null

        val blockExpr = element.parentOfType<RustBlockExprElement>() as? RustBlockExprElement ?: return null
        val block = blockExpr.block ?: return null

        if (block.expr != null
            && block.lbrace.getNextNonCommentSibling() == block.expr) {
            return Context(blockExpr)
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val ctx = findContext(element) ?: return
        val block = ctx.blockExpr
        val blockBody = ctx.blockExpr.block?.expr ?: return
        val relativeCaretPosition = editor.caretModel.offset - blockBody.textOffset

        val offset = (block.replace(blockBody) as RustExprElement).textOffset
        editor.caretModel.moveToOffset(offset + relativeCaretPosition)

    }

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        return findContext(element) != null
    }
}
