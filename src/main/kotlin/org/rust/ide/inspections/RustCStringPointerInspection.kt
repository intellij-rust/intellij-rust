package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RustCallExprElement
import org.rust.lang.core.psi.RustElementVisitor
import org.rust.lang.core.psi.RustMethodCallExprElement
import org.rust.lang.core.psi.RustPathExprElement

class RustCStringPointerInspection : RustLocalInspectionTool() {
    override fun getDisplayName(): String = "Unsafe CString pointer"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : RustElementVisitor() {
            override fun visitMethodCallExpr(expr: RustMethodCallExprElement) {
                if (expr.identifier.text == "as_ptr") {
                    val methodCallExpr = expr.expr
                    if (methodCallExpr is RustMethodCallExprElement && methodCallExpr.identifier.text == "unwrap") {
                        val callExpr = methodCallExpr.expr
                        if (callExpr is RustCallExprElement) {
                            val pathExpr = callExpr.expr
                            if (pathExpr is RustPathExprElement
                                && pathExpr.path.identifier?.text == "new"
                                && pathExpr.path.path?.identifier?.text == "CString") {
                                holder.registerProblem(expr, displayName)
                            }
                        }
                    }
                }
            }
        }
    }
}
