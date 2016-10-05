package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RustElementVisitor
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustUnaryExprElement

/**
 * Checks for usage of double negation, which is a no-op in Rust but might be misleading for
 * programmers with background in languages that have prefix operators.
 *
 * Analogue of Clippy's double_neg.
 */
class RustDoubleNegInspection : RustLocalInspectionTool() {
    override fun getDisplayName(): String = "Double negation"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : RustElementVisitor() {
            override fun visitUnaryExpr(expr: RustUnaryExprElement) {
                if (isNegation(expr) && isNegation(expr.expr)) {
                    holder.registerProblem(expr, "--x could be misinterpreted as a pre-decrement, but effectively is a no-op")
                }
            }
        }
    }

    private fun isNegation(el: RustExprElement?) =
        el != null && el is RustUnaryExprElement && el.minus != null
}
