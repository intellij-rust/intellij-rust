/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.ide.annotator.fixes.AddMutableFix
import org.rust.lang.core.psi.RsBinaryExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.AssignmentOp
import org.rust.lang.core.psi.ext.operatorType
import org.rust.lang.core.types.isMutable

class RsReassignImmutableInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitBinaryExpr(expr: RsBinaryExpr) {
                if (expr.isAssignBinaryExpr() && !expr.left.isMutable) {
                    registerProblem(holder, expr, expr.left)
                }
            }
        }

    private fun registerProblem(holder: ProblemsHolder, expr: RsExpr, nameExpr: RsExpr) {
        val fix = AddMutableFix.createIfCompatible(nameExpr).let { if (it == null) emptyArray() else arrayOf(it) }
        holder.registerProblem(expr, "Re-assignment of immutable variable [E0384]", *fix)
    }

}

private fun RsExpr?.isAssignBinaryExpr(): Boolean {
    val op = this as? RsBinaryExpr ?: return false
    return op.operatorType is AssignmentOp
}
