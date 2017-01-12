package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.descendentsOfType
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.utils.isNegation
import org.rust.lang.utils.negateToString

class DemorgansLawIntention : RustElementBaseIntentionAction<DemorgansLawIntention.Context>() {
    override fun getFamilyName() = "DeMorgan's Law"

    private fun setTextForElement(element: RsBinaryExpr) {
        val binaryExpression = element
        text = when (binaryExpression.operatorType) {
            RustTokenElementTypes.ANDAND -> "DeMorgan's Law, Replace '&&' with '||'"
            RustTokenElementTypes.OROR -> "DeMorgan's Law, Replace '||' with '&&'"
            else -> ""
        }
    }

    data class Context(
        val binaryExpr: RsBinaryExpr,
        val binaryOpType: IElementType
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val binExpr = element.parentOfType<RsBinaryExpr>() ?: return null
        val opType = binExpr.operatorType
        if (opType == RustTokenElementTypes.ANDAND || opType == RustTokenElementTypes.OROR) {
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
            val binaryExprs = topBinExpr.descendentsOfType<RsBinaryExpr>().filter { e ->
                !(e.operatorType != opType || e.parent is RsBinaryExpr && (e.parent as RsBinaryExpr).operatorType == opType)
            }

            binaryExprs.forEach {
                applyDemorgan(project, it, opType)
            }
        }
    }


    private fun applyDemorgan(project: Project, topBinExpr: RsBinaryExpr, opType: IElementType) {
        val converted = convertConjunctionExpression(topBinExpr, opType) ?: return

        var expressionToReplace: RsExpr = topBinExpr
        var expString = "!($converted)"
        val parent = topBinExpr.parent.parent
        if (parent.isNegation()) {
            expressionToReplace = parent as RsExpr
            expString = converted
        }
        val newExpr = RustPsiFactory(project).createExpression(expString)

        expressionToReplace.replace(newExpr)
    }

    private fun isConjunctionExpression(expression: RsExpr, opType: IElementType): Boolean {
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
                return if (c is RsBinaryExpr
                    && c.operatorType != RustTokenElementTypes.ANDAND
                    && c.operatorType != RustTokenElementTypes.OROR) {
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

    private fun convertConjunctionExpression(exp: RsBinaryExpr, opType: IElementType): String? {
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
            if (opType == RustTokenElementTypes.ANDAND) "||" else "&&"
        } else {
            exp.operator.text
        }

        return "$lhsText $flippedConjunction $rhsText"
    }
}
