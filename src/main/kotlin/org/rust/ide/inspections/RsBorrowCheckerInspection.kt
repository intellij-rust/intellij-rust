package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.ide.annotator.fixes.AddMutableFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.isMutable

class RsBorrowCheckerInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitMethodCallExpr(call: RsMethodCallExpr) {
                val fn = call.reference.resolve() as? RsFunction ?: return
                if (checkMethodRequiresMutable(call, fn)) {
                    registerProblem(holder, call.expr, call.expr)
                }
            }

            override fun visitUnaryExpr(unaryExpr: RsUnaryExpr) {
                val expr = unaryExpr.expr ?: return
                if (unaryExpr.operatorType == UnaryOperator.REF_MUT && !expr.isMutable) {
                    registerProblem(holder, expr, expr)
                }
            }
        }

    private fun registerProblem(holder: ProblemsHolder, expr: RsExpr, nameExpr: RsExpr) {
        val fix = AddMutableFix.createIfCompatible(nameExpr).let { if (it == null) emptyArray() else arrayOf(it) }
        holder.registerProblem(expr, "Cannot borrow immutable local variable `${nameExpr.text}` as mutable", *fix)
    }

    private fun checkMethodRequiresMutable(o: RsMethodCallExpr, fn: RsFunction): Boolean {
        if (!o.expr.isMutable &&
            fn.selfParameter != null &&
            fn.selfParameter?.isMut ?: false &&
            fn.selfParameter?.isRef ?: false) {
            val typeRef = o.parentOfType<RsImplItem>()?.typeReference as? RsRefLikeType ?: return true
            return !typeRef.isMut
        }
        return false
    }

}
