/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.ide.annotator.fixes.AddMutableFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.isMutable

class RsBorrowCheckerInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitMethodCall(o: RsMethodCall) {
                val fn = o.reference.resolve() as? RsFunction ?: return
                if (checkMethodRequiresMutable(o, fn)) {
                    registerProblem(holder, o.parentDotExpr.expr, o.parentDotExpr.expr)
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

    private fun checkMethodRequiresMutable(o: RsMethodCall, fn: RsFunction): Boolean {
        if (!o.parentDotExpr.expr.isMutable &&
            fn.selfParameter != null &&
            fn.selfParameter?.mutability?.isMut ?: false &&
            fn.selfParameter?.isRef ?: false) {
            val typeRef = o.parentOfType<RsImplItem>()?.typeReference?.typeElement as? RsRefLikeType ?: return true
            return !typeRef.mutability.isMut
        }
        return false
    }

}
