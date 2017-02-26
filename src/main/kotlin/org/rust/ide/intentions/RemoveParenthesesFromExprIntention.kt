package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsParenExpr
import org.rust.lang.core.psi.ext.parentOfType

class RemoveParenthesesFromExprIntention : RsElementBaseIntentionAction<RsParenExpr>() {
    override fun getText(): String = "Remove parentheses from expression"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsParenExpr? =
        element.parentOfType<RsParenExpr>()

    override fun invoke(project: Project, editor: Editor, ctx: RsParenExpr) {
        ctx.replace(ctx.expr)
    }
}
