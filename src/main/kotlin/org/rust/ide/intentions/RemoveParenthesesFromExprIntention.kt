/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict

class RemoveParenthesesFromExprIntention : RsElementBaseIntentionAction<RsParenExpr>() {
    override fun getText(): String = "Remove parentheses from expression"
    override fun getFamilyName(): String = text

    fun isAvailable(expr: RsParenExpr?): Boolean {
        if (expr?.ancestorStrict<RsCondition>() == null && expr?.ancestorStrict<RsMatchExpr>() == null) return true
        val child = expr.children.singleOrNull()
        return when (child) {
            is RsStructLiteral -> false
            is RsBinaryExpr -> child.exprList.all { it !is RsStructLiteral }
            else -> true
        }
    }

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsParenExpr? {
        val parenExpr = element.ancestorStrict<RsParenExpr>()
        return if (isAvailable(parenExpr)) parenExpr else null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsParenExpr) {
        ctx.replace(ctx.expr)
    }
}
