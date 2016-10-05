package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.ide.inspections.fixes.RemoveRefFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.RustReferenceType
import org.rust.lang.core.types.util.resolvedType

/**
 * Checks for calls to std::mem::drop with a reference instead of an owned value. Analogue of Clippy's drop_ref.
 * Quick fix: Use the owned value as the argument.
 */
class RustDropRefInspection : RustLocalInspectionTool() {
    private object Constants {
        val MESSAGE = "Call to std::mem::drop with a reference argument. Dropping a reference does nothing"
    }

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
            if (resEl is RustFnItemElement && resEl.canonicalCratePath.toString() == "::mem::drop") {
                val args = expr.argList.exprList
                if (args.size == 1) {
                    val arg = args[0]
                    if (arg.resolvedType is RustReferenceType) {
                        if (arg.text[0] == '&') {
                            holder.registerProblem(expr, Constants.MESSAGE, RemoveRefFix(arg, "Call with owned value"))
                        } else {
                            holder.registerProblem(expr, Constants.MESSAGE)
                        }
                    }
                }
            }
        }
    }
}
