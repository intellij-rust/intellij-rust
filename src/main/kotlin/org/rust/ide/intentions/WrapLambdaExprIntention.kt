package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockExprElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustLambdaExprElement
import org.rust.lang.core.psi.util.parentOfType

class WrapLambdaExprIntention : PsiElementBaseIntentionAction() {
    override fun getText() = "Add braces to lambda expression"
    override fun getFamilyName() = getText()
    override fun startInWriteAction() = true

    private data class Context(
        val lambdaExpr: RustLambdaExprElement
    )

    private fun findContext(element: PsiElement): Context? {
        if (!element.isWritable) return null

        val lambdaExpr = element.parentOfType<RustLambdaExprElement>() ?: return null
        val body = lambdaExpr.expr ?: return null
        if (body !is RustBlockExprElement) {
            return Context(lambdaExpr)
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val ctx = findContext(element) ?: return
        val lambdaBody = ctx.lambdaExpr.expr ?: return
        val bodyStr = ctx.lambdaExpr.expr?.text ?: return
        val blockExpr = RustElementFactory.createBlockExpr(project, bodyStr) ?: return
        lambdaBody.replace(blockExpr)
    }

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        return findContext(element) != null
    }
}
