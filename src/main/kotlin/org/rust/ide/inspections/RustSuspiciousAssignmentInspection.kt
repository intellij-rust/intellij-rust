package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.psi.*

/**
 * Checks for use of the non-existent =*, =! and =- operators that are probably typos but can be compiled.
 * Analogue of Clippy's suspicious_assignment_formatting.
 * QuickFix 1: Change `a =? b` to `a ?= b`
 * QuickFix 2: Change `a =? b` to `a = ?b`
 */
class RustSuspiciousAssignmentInspection : RustLocalInspectionTool() {
    private object Constants {
        val LONG_TEXT_THRESHOLD = 10
        val LONG_TEXT_SUBST = ".."
    }

    override fun getDisplayName(): String = "Suspicious assignment"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : RustElementVisitor() {
            override fun visitBinaryExpr(expr: RustBinaryExprElement) {
                if (expr.operator.text == "=") {
                    val rightExpr = findUnaryExpr(expr.right)
                    if (rightExpr is RustUnaryExprElement && rightExpr.expr != null) {
                        val uExpr = rightExpr.expr
                        val op = rightExpr.text[0]
                        if (uExpr != null
                            && (op == '-' || op == '*' || op == '!')
                            && expr.operator.distanceTo(rightExpr) == 1
                            && expr.operator.distanceTo(uExpr) > 2) {
                            val uExprOffset = uExpr.textRange.startOffset - expr.left.textRange.startOffset
                            val left = expr.left.text.compact()
                            val right = expr.text.substring(uExprOffset).compact()
                            val right2 = if (right == Constants.LONG_TEXT_SUBST) "($op$right)" else "$op$right"
                            val subst1 = "$left $op= $right"
                            val subst2 = "$left = $right2"
                            val substRange = TextRange(expr.left.textRange.endOffset, uExpr.textRange.startOffset)
                            holder.registerProblem(
                                expr,
                                TextRange(expr.left.text.length, uExprOffset),
                                "Suspicious assignment. Did you mean `$subst1` or `$subst2`?",
                                SubstituteTextFix(expr.containingFile, substRange, " $op= ", "Change to `$subst1`"),
                                SubstituteTextFix(expr.containingFile, substRange, " = $op", "Change to `$subst2`"))
                        }
                    }
                }
            }
        }
    }

    /**
     * Computes the distance between the start points of this PSI element and another one.
     */
    private fun PsiElement.distanceTo(other: PsiElement) = other.textRange.startOffset - textRange.startOffset

    private fun String.compact() = if (length <= Constants.LONG_TEXT_THRESHOLD) this else Constants.LONG_TEXT_SUBST

    /**
     * Finds the first unary expression on the left side of the given expression.
     */
    private fun findUnaryExpr(el: RustExprElement?): RustUnaryExprElement? = when (el) {
        is RustUnaryExprElement -> el
        is RustBinaryExprElement -> findUnaryExpr(el.left)
        else -> null
    }
}
