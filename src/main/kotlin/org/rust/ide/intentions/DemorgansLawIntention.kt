package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.descendentsOfType
import org.rust.lang.core.psi.util.parentOfType

class DemorgansLawIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName() = text
    override fun startInWriteAction() = true

    private fun setTextForElement(element: PsiElement) {
        val binaryExpression = element as RustBinaryExprElement
        text = when (binaryExpression.operatorType) {
            RustTokenElementTypes.ANDAND -> "DeMorgan's Law, Replace '&&' with '||'"
            RustTokenElementTypes.OROR -> "DeMorgan's Law, Replace '||' with '&&'"
            else -> ""
        }
    }

    private data class Context(
        val binaryExpr: RustBinaryExprElement,
        val binaryOpType: IElementType
    )

    private fun findContext(element: PsiElement): Context? {
        if (!element.isWritable) return null

        val binExpr = element.parentOfType<RustBinaryExprElement>() ?: return null
        val opType = binExpr.operatorType
        if (opType == RustTokenElementTypes.ANDAND || opType == RustTokenElementTypes.OROR) {
            return Context(binExpr, opType)
        }
        return null
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (findContext(element) != null) {
            setTextForElement(findContext(element)?.binaryExpr as PsiElement)
            return true
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val (binExpr, opType) = findContext(element) ?: return

        var topBinExpr = binExpr
        var isAllSameOpType = true
        while (topBinExpr.parent is RustBinaryExprElement
            || (topBinExpr.parent is RustParenExprElement
            && isNegation(topBinExpr.parent.parent)
            && topBinExpr.parent.parent.parent is RustBinaryExprElement)) {
            topBinExpr = if (topBinExpr.parent is RustBinaryExprElement) topBinExpr.parent as RustBinaryExprElement else topBinExpr.parent.parent.parent as RustBinaryExprElement
            isAllSameOpType = topBinExpr.parent is RustBinaryExprElement && topBinExpr.operatorType == opType
        }

        if (isAllSameOpType) {
            applyDemorgan(project, topBinExpr, opType)
        } else {
            val binaryExprs = topBinExpr.descendentsOfType<RustBinaryExprElement>().filter { e ->
                !(e.operatorType != opType || e.parent is RustBinaryExprElement && (e.parent as RustBinaryExprElement).operatorType == opType)
            }

            binaryExprs.forEach {
                applyDemorgan(project, it, opType)
            }
        }
    }

    private fun applyDemorgan(project: Project, topBinExpr: RustBinaryExprElement, opType: IElementType) {
        val converted = convertConjunctionExpression(topBinExpr, opType) ?: return

        var expressionToReplace: RustExprElement = topBinExpr
        var expString = "!($converted)"
        val parent = topBinExpr.parent.parent
        if (isNegation(parent)) {
            expressionToReplace = parent as RustExprElement
            expString = converted
        }
        val newExpr = RustElementFactory.createExpression(project, expString) ?: return

        expressionToReplace.replace(newExpr)
    }

    private fun isConjunctionExpression(expression: RustExprElement, opType: IElementType): Boolean {
        return expression is RustBinaryExprElement && expression.operatorType == opType
    }

    private fun isNegation(expression: PsiElement): Boolean {
        if (expression !is RustUnaryExprElement) return false
        expression.excl ?: return false
        return true
    }

    private fun negateBinaryExpr(expr: RustBinaryExprElement): String {
        val lhs = expr.left.text
        val rhs = expr.right?.text ?: ""
        val op = when (expr.operatorType) {
            RustTokenElementTypes.EQEQ -> "!="
            RustTokenElementTypes.EXCLEQ -> "=="
            RustTokenElementTypes.GT -> "<="
            RustTokenElementTypes.LT -> ">="
            RustTokenElementTypes.GTEQ -> "<"
            RustTokenElementTypes.LTEQ -> ">"
            else -> null
        }
        return if (op != null) "$lhs $op $rhs" else "!(" + expr.text + ")"
    }

    private fun convertLeafExpression(condition: RustExprElement): String {
        if (isNegation(condition)) {
            val negated = (condition as RustUnaryExprElement).expr ?: return ""
            return negated.text
        } else {
            if (condition is RustParenExprElement) {
                var c = condition.expr
                var level = 1
                while (c is RustParenExprElement) {
                    level += 1
                    c = c.expr
                }
                return if (c is RustBinaryExprElement
                    && c.operatorType != RustTokenElementTypes.ANDAND
                    && c.operatorType != RustTokenElementTypes.OROR) {
                    "${"(".repeat(level)}${negateBinaryExpr(c)}${")".repeat(level)}"
                } else {
                    "!" + condition.text
                }
            } else if (condition is RustBinaryExprElement) {
                return negateBinaryExpr(condition)
            } else {
                return "!" + condition.text
            }
        }
    }

    private fun convertConjunctionExpression(exp: RustBinaryExprElement, opType: IElementType): String? {
        val lhs = exp.left
        val lhsText = if (isConjunctionExpression(lhs, opType)) {
            convertConjunctionExpression(lhs as RustBinaryExprElement, opType)
        } else {
            convertLeafExpression(lhs)
        }

        exp.right ?: return null

        val rhs = exp.right ?: return null
        val rhsText = if (isConjunctionExpression(rhs, opType)) {
            convertConjunctionExpression(rhs as RustBinaryExprElement, opType)
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
