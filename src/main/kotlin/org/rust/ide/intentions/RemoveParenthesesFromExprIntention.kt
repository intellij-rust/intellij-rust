package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsCondition
import org.rust.lang.core.psi.RsMatchExpr
import org.rust.lang.core.psi.RsParenExpr
import org.rust.lang.core.psi.RsStructLiteral
import org.rust.lang.core.psi.ext.parentOfType

class RemoveParenthesesFromExprIntention : RsElementBaseIntentionAction<RsParenExpr>() {
    override fun getText(): String = "Remove parentheses from expression"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsParenExpr? {
        val parenExpr = element.parentOfType<RsParenExpr>()

        return if (parenExpr?.parentOfType<RsCondition>() != null || parenExpr?.parentOfType<RsMatchExpr>() != null) {
            when (parenExpr.children.singleOrNull()) {
                null, is RsStructLiteral -> null
                else -> parenExpr
            }
        } else parenExpr
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsParenExpr) {
        ctx.replace(ctx.expr)
    }
}
