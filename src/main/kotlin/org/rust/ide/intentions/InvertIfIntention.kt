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
import org.rust.lang.utils.negate

class InvertIfIntention : RsElementBaseIntentionAction<InvertIfIntention.Context>() {
    data class Context(
        val ifExpr: RsIfExpr,
        val condition: RsCondition,
        val thenBlock: RsBlock,
        val elseBlock: RsBlock
    )


    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val ifExpr = element.ancestorStrict<RsIfExpr>() ?: return null
        val condition = getSuitableCondition(ifExpr) ?: return null
        val thenBlock = ifExpr.block ?: return null
        val elseBlock = ifExpr.elseBranch?.block ?: return null

        return Context(ifExpr, condition, thenBlock, elseBlock)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val negatedCondition = ctx.condition.expr.negate() as? RsExpr ?: return

        val newIfExpr = RsPsiFactory(project).createIfElseExpression(negatedCondition, ctx.elseBlock, ctx.thenBlock)
        val replacedIfExpr = ctx.ifExpr.replace(newIfExpr) as RsIfExpr

        val newCondition = replacedIfExpr.condition!!.expr

        if (newCondition is RsUnaryExpr && newCondition.excl != null) {
            val binaryExpression = (newCondition.expr as? RsParenExpr)?.expr as? RsBinaryExpr ?: return
            DemorgansLawIntention().invoke(project, editor, binaryExpression.binaryOp)
        }
    }

    private fun getSuitableCondition(ifExpr: RsIfExpr): RsCondition? =
        ifExpr.condition?.takeIf { it.let == null }

    override fun getFamilyName(): String = text

    override fun getText() = "Invert if condition"
}
