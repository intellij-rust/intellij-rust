package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.ide.inspections.fixes.RemoveRefFix
import org.rust.lang.core.psi.RustCallExprElement
import org.rust.lang.core.psi.RustElementVisitor
import org.rust.lang.core.psi.RustPathExprElement
import org.rust.lang.core.psi.RustUnaryExprElement
import org.rust.lang.core.psi.impl.RustFnItemElementImpl
import org.rust.lang.core.psi.impl.RustPathExprElementImpl

/**
 * Checks for calls to std::mem::drop with a reference instead of an owned value.
 *
 * Analogue of Clippy's drop_ref.
 */
class RustDropRefInspection : RustLocalInspectionTool() {
    override fun getDisplayName(): String = "Drop reference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : RustElementVisitor() {
            override fun visitCallExpr(expr: RustCallExprElement) {
                inspectExpr(expr, holder)
            }
        }
    }

    fun inspectExpr(expr: RustCallExprElement, holder: ProblemsHolder) {
        val pathExpr = expr.expr
        if (pathExpr is RustPathExprElement) {
            val resEl = pathExpr.path.reference.resolve()
            if (resEl is RustFnItemElementImpl && resEl.canonicalCratePath.toString() == "::mem::drop") {
                val args = expr.argList.exprList
                if (args.size == 1) {
                    val arg = args[0]
                    if (arg is RustUnaryExprElement) {
                        if (arg.expr is RustPathExprElement) {
                            holder.registerProblem(
                                expr,
                                "Call to std::mem::drop with a reference argument. Dropping a reference does nothing",
                                RemoveRefFix(arg, "Call with owned value"))
                        }
                    }
                }
            }
        }
    }
}
