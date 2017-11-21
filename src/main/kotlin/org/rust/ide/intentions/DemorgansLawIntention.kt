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
import org.rust.lang.core.psi.ext.LogicOp.*
import org.rust.lang.utils.isNegation
import org.rust.lang.utils.negateToString

class DemorgansLawIntention : RsElementBaseIntentionAction<DemorgansLawIntention.Context>() {
    override fun getFamilyName() = "DeMorgan's Law"

    private fun setTextForElement(element: RsBinaryExpr) {
        val binaryExpression = element
        text = when (binaryExpression.operatorType) {
            AND -> "DeMorgan's Law, Replace '&&' with '||'"
            OR -> "DeMorgan's Law, Replace '||' with '&&'"
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
        val (binExpr, opType) = ctx

        var topBinExpr = binExpr
        var isAllSameOpType = true
        while (topBinExpr.parent is RsBinaryExpr
            || (topBinExpr.parent is RsParenExpr
            && topBinExpr.parent.parent.isNegation()
            && topBinExpr.parent.parent.parent is RsBinaryExpr)) {
            topBinExpr = if (topBinExpr.parent is RsBinaryExpr) topBinExpr.parent as RsBinaryExpr else topBinExpr.parent.parent.parent as RsBinaryExpr
            isAllSameOpType = topBinExpr.parent is RsBinaryExpr && topBinExpr.operatorType == opType
        }

        if (isAllSameOpType) {
            applyDemorgan(project, topBinExpr, opType)
        } else {
            val binaryExprs = topBinExpr.descendantsOfType<RsBinaryExpr>().filter { e ->
                !(e.operatorType != opType || e.parent is RsBinaryExpr && (e.parent as RsBinaryExpr).operatorType == opType)
            }

            binaryExprs.forEach {
                applyDemorgan(project, it, opType)
            }
        }
    }


    private fun applyDemorgan(project: Project, topBinExpr: RsBinaryExpr, opType: BinaryOperator) {
        val converted = convertConjunctionExpression(topBinExpr, opType) ?: return

        var expressionToReplace: RsExpr = topBinExpr
        var expString = "!($converted)"
        val parent = topBinExpr.parent.parent
        if (parent.isNegation()) {
            expressionToReplace = parent as RsExpr
            expString = converted
        }
        val newExpr = RsPsiFactory(project).createExpression(expString)

        expressionToReplace.replace(newExpr)
    }

    private fun isConjunctionExpression(expression: RsExpr, opType: BinaryOperator): Boolean {
        return expression is RsBinaryExpr && expression.operatorType == opType
    }

    private fun convertLeafExpression(condition: RsExpr): String {
        if (condition.isNegation()) {
            val negated = (condition as RsUnaryExpr).expr ?: return ""
            return negated.text
        } else {
            if (condition is RsParenExpr) {
                var c = condition.expr
                var level = 1
                while (c is RsParenExpr) {
                    level += 1
                    c = c.expr
                }
                return if (c is RsBinaryExpr && c.operatorType !is LogicOp) {
                    "${"(".repeat(level)}${c.negateToString()}${")".repeat(level)}"
                } else {
                    "!" + condition.text
                }
            } else if (condition is RsBinaryExpr) {
                return condition.negateToString()
            } else {
                return "!" + condition.text
            }
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
