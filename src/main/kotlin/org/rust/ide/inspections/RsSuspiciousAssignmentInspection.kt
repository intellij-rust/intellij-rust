/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.lang.core.psi.RsBinaryExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsUnaryExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.operator

/**
 * Checks for use of the non-existent =*, =! and =- operators that are probably typos but can be compiled.
 * Analogue of Clippy's suspicious_assignment_formatting.
 * QuickFix 1: Change `a =? b` to `a ?= b`
 * QuickFix 2: Change `a =? b` to `a = ?b`
 */
class RsSuspiciousAssignmentInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Suspicious assignment"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RsVisitor() {
            override fun visitBinaryExpr(expr: RsBinaryExpr) {
                if (expr.operator.text != "=") return
                val unaryExpr = findUnaryExpr(expr.right)
                if (unaryExpr !is RsUnaryExpr || unaryExpr.expr == null) return
                val unaryBody = unaryExpr.expr
                val op = unaryExpr.text[0]
                if (unaryBody != null
                    && (op == '-' || op == '*' || op == '!')
                    && expr.operator.distanceTo(unaryExpr) == 1
                    && expr.operator.distanceTo(unaryBody) > 2) {
                    // Here we have:
                    // var =- 12 * (a + 108)
                    // ^^^                   expr.left
                    //     ^                 expr.operator
                    //      ^^^^^^^^^^^^^^^^ expr.right
                    //      ^^^^             unaryExpr
                    //      ^                op
                    //        ^^             unaryBody
                    val uExprOffset = unaryBody.textRange.startOffset - expr.left.textRange.startOffset
                    val left = expr.left.text.compact()
                    val right = expr.text.substring(uExprOffset).compact()
                    val right2 = if (right == LONG_TEXT_SUBST) "($op$right)" else "$op$right"
                    val subst1 = "$left $op= $right"
                    val subst2 = "$left = $right2"
                    val substRange = TextRange(expr.left.textRange.endOffset, unaryBody.textRange.startOffset)
                    val file = expr.containingFile
                    holder.registerProblem(
                        expr,
                        TextRange(expr.left.text.length, uExprOffset),
                        "Suspicious assignment. Did you mean `$subst1` or `$subst2`?",
                        SubstituteTextFix.replace("Change to `$subst1`", file, substRange, " $op= "),
                        SubstituteTextFix.replace("Change to `$subst2`", file, substRange, " = $op"))
                }
            }
        }

    /**
     * Computes the distance between the start points of this PSI element and another one.
     */
    private fun PsiElement.distanceTo(other: PsiElement) = other.textRange.startOffset - textRange.startOffset

    private fun String.compact() = if (length <= LONG_TEXT_THRESHOLD) this else LONG_TEXT_SUBST

    /**
     * Finds the first unary expression on the left side of the given expression.
     */
    private fun findUnaryExpr(el: RsExpr?): RsUnaryExpr? = when (el) {
        is RsUnaryExpr -> el
        is RsBinaryExpr -> findUnaryExpr(el.left)
        else -> null
    }

    private companion object {
        val LONG_TEXT_THRESHOLD = 10
        val LONG_TEXT_SUBST = ".."
    }
}
