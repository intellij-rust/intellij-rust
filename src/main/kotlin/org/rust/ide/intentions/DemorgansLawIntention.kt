/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.LogicOp.AND
import org.rust.lang.core.psi.ext.LogicOp.OR
import org.rust.lang.utils.isNegation
import org.rust.lang.utils.negateToString

class DemorgansLawIntention : RsElementBaseIntentionAction<DemorgansLawIntention.Context>() {
    override fun getFamilyName() = "DeMorgan's law"

    private fun setTextForElement(element: RsBinaryExpr) {
        text = when (element.operatorType) {
            AND -> "DeMorgan's law, replace '&&' with '||'"
            OR -> "DeMorgan's law, replace '||' with '&&'"
            else -> ""
        }
    }

    data class Context(
        val binaryExpr: RsBinaryExpr,
        val binaryOpType: BinaryOperator
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val binExpr = element.ancestorStrict<RsBinaryExpr>() ?: return null
        val opType = binExpr.operatorType
        if (opType is LogicOp) {
            setTextForElement(binExpr)
            return Context(binExpr, opType)
        }
        return null
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (binaryExpr, opType) = ctx
        val topBinaryExpr = binaryExpr.getTopmostBinaryExprWithSameOpType()
        applyDemorgan(project, topBinaryExpr, opType)
    }

    private fun RsBinaryExpr.getTopmostBinaryExprWithSameOpType(): RsBinaryExpr {
        val parent = parent
        return if (parent is RsBinaryExpr && parent.binaryOp.op == binaryOp.op) {
            parent.getTopmostBinaryExprWithSameOpType()
        } else {
            this
        }
    }


    private fun applyDemorgan(project: Project, topBinExpr: RsBinaryExpr, opType: BinaryOperator) {
        val converted = convertConjunctionExpression(topBinExpr, opType) ?: return

        val parent = topBinExpr.parent?.parent
        val (expString, expressionToReplace) = if (parent != null && parent.isNegation()) {
            val grandParent = parent.parent
            val convertedOpType = if (opType == OR) AND else OR
            val canOmitParens = grandParent.canOmitParensFor(convertedOpType)
            val expString = if (canOmitParens) converted else "($converted)"
            expString to parent as RsExpr
        } else {
            "!($converted)" to topBinExpr
        }
        val newExpr = RsPsiFactory(project).createExpression(expString)

        expressionToReplace.replace(newExpr)
    }

    private fun PsiElement.canOmitParensFor(opType: LogicOp): Boolean {
        if (this !is RsBinaryExpr) return true
        return when (binaryOp.operatorType) {
            AND -> opType == AND
            OR -> true
            else -> false
        }
    }

    private fun isConjunctionExpression(expression: RsExpr, opType: BinaryOperator): Boolean {
        return expression is RsBinaryExpr && expression.operatorType == opType
    }

    private fun convertLeafExpression(condition: RsExpr): String {
        if (condition.isNegation()) {
            return (condition as RsUnaryExpr).expr?.text ?: ""
        }

        return when (condition) {
            is RsParenExpr -> {
                var c = condition.expr
                var level = 1
                while (c is RsParenExpr) {
                    level += 1
                    c = c.expr
                }
                if (c is RsBinaryExpr && c.operatorType !is LogicOp) {
                    "${"(".repeat(level)}${c.negateToString()}${")".repeat(level)}"
                } else {
                    "!" + condition.text
                }
            }
            is RsBinaryExpr -> condition.negateToString()
            else -> "!" + condition.text
        }
    }

    private fun convertConjunctionExpression(exp: RsBinaryExpr, opType: BinaryOperator): String? {
        val lhs = exp.left
        val lhsText = if (isConjunctionExpression(lhs, opType)) {
            convertConjunctionExpression(lhs as RsBinaryExpr, opType)
        } else {
            convertLeafExpression(lhs)
        }

        exp.right ?: return null

        val rhs = exp.right ?: return null
        val rhsText = if (isConjunctionExpression(rhs, opType)) {
            convertConjunctionExpression(rhs as RsBinaryExpr, opType)
        } else {
            convertLeafExpression(rhs)
        }

        val flippedConjunction = if (exp.operatorType == opType) {
            if (opType == AND) "||" else "&&"
        } else {
            exp.operator.text
        }

        return "$lhsText $flippedConjunction $rhsText"
    }
}
