/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.ide.utils.PsiModificationUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class MergeIfsIntention : RsElementBaseIntentionAction<MergeIfsIntention.Context>() {
    override fun getText(): String = RsBundle.message("intention.name.merge.with.nested.if.expression")
    override fun getFamilyName(): String = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    class Context(
        val ifExprBlock: RsBlock,
        val nestedIfExprBlock: RsBlock?,
        val ifCondition: RsExpr,
        val nestedIfCondition: RsExpr,
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val ifExpr = element.ancestorStrict<RsIfExpr>() ?: return null
        if (element != ifExpr.`if`) return null

        val ifExprBlock = ifExpr.block ?: return null
        val nestedIfExpr = ifExprBlock.singleTailStmt()?.expr as? RsIfExpr ?: return null

        val ifCondition = ifExpr.condition?.expr ?: return null
        val nestedIfCondition = nestedIfExpr.condition ?: return null
        val nestedIfExprBlock = nestedIfExpr.block

        if (ifCondition.descendantOfTypeOrSelf<RsLetExpr>() != null) return null
        if (nestedIfCondition.expr?.descendantOfTypeOrSelf<RsLetExpr>() != null) return null
        if (ifExpr.elseBranch != null || nestedIfExpr.elseBranch != null) return null
        if (!PsiModificationUtil.canReplace(ifCondition)) return null
        if (nestedIfExprBlock != null && !PsiModificationUtil.canReplace(ifExprBlock)) return null

        return Context(
            ifExprBlock,
            nestedIfExprBlock,
            ifCondition,
            nestedIfCondition.expr ?: return null,
        )
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val condition = createConjunction(ctx.ifCondition, ctx.nestedIfCondition)
        ctx.ifCondition.replace(condition)
        ctx.ifExprBlock.replace(ctx.nestedIfExprBlock ?: return)
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
}
