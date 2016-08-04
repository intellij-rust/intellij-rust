package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RustBlockExprElement
import org.rust.lang.core.psi.RustLambdaExprElement
import org.rust.lang.core.psi.util.getNextNonCommentSibling
import org.rust.lang.core.psi.util.parentOfType

class UnwrapLambdaExprIntention : PsiElementBaseIntentionAction() {
    override fun getText() = "Remove braces from lambda expression"
    override fun getFamilyName() = getText()
    override fun startInWriteAction() = true

    private data class Context(
        val lambdaExpr: RustLambdaExprElement,
        val blockExpr: RustBlockExprElement
    )

    private fun findContext(element: PsiElement): Context? {
        if (!element.isWritable) return null

        // find first parent which is either block or lambda
        // if we get lambda, than return false as we are inside "unblocked" lambda
        val blockExpr = PsiTreeUtil.findFirstParent(element, blockOrLambda) as? RustBlockExprElement ?: return null
        val block = blockExpr.block ?: return null

        val lambdaExpr = element.parentOfType<RustLambdaExprElement>() ?: return null

        // check whether our block's parent is lambda to avoid false positives
        // like nested blocks in lambda (we are not interested in this case as we can
        // be sure that cursor isn't at brace; well, I don't know what about `|x| {<caret>{ x }}`,
        // but it's possibly rare edge case), or just some random block in code
        if (blockExpr.parent is RustLambdaExprElement
            && block.expr != null
            && block.lbrace.getNextNonCommentSibling() == block.expr) {
            return Context(lambdaExpr, blockExpr)
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val ctx = findContext(element) ?: return
        val lambdaBody = ctx.lambdaExpr.expr ?: return
        val blockBody = ctx.blockExpr.block?.expr ?: return
        lambdaBody.replace(blockBody) ?: return
    }

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        return findContext(element) != null
    }

    private val blockOrLambda = Condition<PsiElement> { element ->
        element is RustBlockExprElement || element is RustLambdaExprElement
    }
}
