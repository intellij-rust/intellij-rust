/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBinaryExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsIfExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.LogicOp
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.operatorType

class MergeIfsIntention : RsElementBaseIntentionAction<MergeIfsIntention.Context>() {

    override fun getText(): String = "Merge with the nested 'if' expression"
    override fun getFamilyName(): String = text

    class Context(
        val ifExpr: RsIfExpr,
        val nestedIfExpr: RsIfExpr,
        val ifCondition: RsExpr,
        val nestedIfCondition: RsExpr,
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val ifExpr = element.ancestorStrict<RsIfExpr>() ?: return null
        if (element != ifExpr.`if`) return null

        val block = ifExpr.block ?: return null
        if (block.stmtList.isNotEmpty()) return null
        val nestedIfExpr = block.expr as? RsIfExpr ?: return null

        val ifCondition = ifExpr.condition ?: return null
        val nestedIfCondition = nestedIfExpr.condition ?: return null
        if (ifCondition.let != null || nestedIfCondition.let != null) return null
        if (ifExpr.elseBranch != null || nestedIfExpr.elseBranch != null) return null

        return Context(
            ifExpr,
            nestedIfExpr,
            ifCondition.expr ?: return null,
            nestedIfCondition.expr ?: return null,
        )
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val condition = createConjunction(ctx.ifCondition, ctx.nestedIfCondition)
        ctx.ifExpr.condition?.expr?.replace(condition)
        ctx.ifExpr.block?.replace(ctx.nestedIfExpr.block ?: return)
    }
}

private fun createConjunction(expr1: RsExpr, expr2: RsExpr): RsExpr {
    val text1 = expr1.getTextForConjunctionOperand()
    val text2 = expr2.getTextForConjunctionOperand()
    return RsPsiFactory(expr1.project).createExpression("$text1 && $text2")
}

private fun RsExpr.getTextForConjunctionOperand(): String =
    if (this is RsBinaryExpr && operatorType == LogicOp.OR) {
        "($text)"
    } else {
        text
    }
