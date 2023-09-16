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
import org.rust.ide.utils.skipParenExprDown
import org.rust.ide.utils.skipParenExprUp
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class SplitIfIntention : RsElementBaseIntentionAction<SplitIfIntention.Context>() {
    override fun getText(): String = RsBundle.message("intention.name.split.into.if.s")
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.split.if")

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    data class Context(
        val binaryOp: RsBinaryOp,
        val operatorType: LogicOp,
        val conditionExpr: RsExpr,
        val ifExpr: RsIfExpr
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val binExpr = element.ancestorStrict<RsBinaryExpr>() ?: return null
        if (binExpr.operatorType !is LogicOp) return null
        val binaryOp = binExpr.binaryOp
        val operatorType = binaryOp.operatorType as? LogicOp ?: return null
        if (element.parent != binaryOp) return null
        val condition = binExpr.findCondition() ?: return null
        if (condition.expr?.descendantOfTypeOrSelf<RsLetExpr>() != null) return null
        val conditionExpr = condition.skipParenExprDown() ?: return null
        val ifStatement = condition.ancestorOrSelf<RsIfExpr>() ?: return null
        if (!PsiModificationUtil.canReplace(ifStatement)) return null
        return Context(binaryOp, operatorType, conditionExpr, ifStatement)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (binaryOp, operatorType, conditionExpr, ifStatement) = ctx
        val thenBranch = ifStatement.block?.text ?: "{ }"
        val elseBranch = ifStatement.elseBranch?.text ?: ""

        val leftCondition = leftPart(conditionExpr, binaryOp)
        val rightCondition = rightPart(conditionExpr, binaryOp)
        val generatedCode = when (operatorType) {
            LogicOp.AND -> createAndAnd(leftCondition, rightCondition, thenBranch, elseBranch)
            LogicOp.OR -> createOrOr(leftCondition, rightCondition, thenBranch, elseBranch)
        }

        val newIfStatement = RsPsiFactory(project).createExpression(generatedCode) as RsIfExpr
        ifStatement.replace(newIfStatement)
    }
}

private fun leftPart(condition: RsExpr, op: RsBinaryOp): String = condition.text.substring(0, op.textOffset - condition.textOffset)

private fun rightPart(condition: RsExpr, op: RsBinaryOp): String = condition.text.substring(op.textOffset + op.textLength - condition.textOffset)

private fun RsBinaryExpr.findCondition(): RsCondition? {
    var parent = skipParenExprUp().parent
    while (parent is RsBinaryExpr && parent.operatorType == operatorType) {
        parent = parent.parent
    }
    return parent as? RsCondition
}

private fun createOrOr(leftCondition: String, rightCondition: String, thenBranch: String, elseBranch: String): String = "if $leftCondition $thenBranch else if $rightCondition $thenBranch $elseBranch"

private fun createAndAnd(leftCondition: String, rightCondition: String, thenBranch: String, elseBranch: String): String = "if $leftCondition { if $rightCondition $thenBranch $elseBranch } $elseBranch"
