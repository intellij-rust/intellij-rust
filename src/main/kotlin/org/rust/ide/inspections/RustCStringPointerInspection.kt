package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsMethodCallExpr
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.RsVisitor

class RustCStringPointerInspection : RustLocalInspectionTool() {
    override fun getDisplayName() = "Unsafe CString pointer"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitMethodCallExpr(expr: RsMethodCallExpr) {
                if (expr.identifier.text != "as_ptr") return

                val methodCallExpr = expr.expr
                if (methodCallExpr !is RsMethodCallExpr || methodCallExpr.identifier.text != "unwrap") return
                val callExpr = methodCallExpr.expr as? RsCallExpr ?: return

                val pathExpr = callExpr.expr
                if (pathExpr is RsPathExpr
                    && pathExpr.path.identifier?.text == "new"
                    && pathExpr.path.path?.identifier?.text == "CString") {
                    holder.registerProblem(expr, displayName)
                }
            }
        }
}
