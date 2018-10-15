/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.isAssignBinaryExpr
import org.rust.lang.core.psi.ext.unwrapParenExprs
import org.rust.lang.core.types.isDereference
import org.rust.lang.core.types.isMutable
import org.rust.lang.core.types.ty.TyPointer
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsAssignToImmutableInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitBinaryExpr(expr: RsBinaryExpr) {
                if (expr.isAssignBinaryExpr) checkAssignment(expr, holder)
            }
        }

    private fun checkAssignment(expr: RsBinaryExpr, holder: ProblemsHolder) {
        val left = unwrapParenExprs(expr.left)
        if (left.isMutable) return

        when (left) {
            is RsDotExpr -> registerProblem(holder, expr, "field of immutable binding")
            is RsIndexExpr -> registerProblem(holder, expr, "indexed content of immutable binding")
            is RsUnaryExpr -> if (left.isDereference) registerDereferenceProblem(left, holder, expr)
        }
    }

    private fun registerDereferenceProblem(left: RsUnaryExpr, holder: ProblemsHolder, expr: RsBinaryExpr) {
        when (left.expr?.type) {
            is TyReference -> registerProblem(holder, expr, "immutable borrowed content")
            is TyPointer -> registerProblem(holder, expr, "immutable dereference of raw pointer")
        }
    }

    private fun registerProblem(holder: ProblemsHolder, expr: RsExpr, message: String) {
        RsDiagnostic.CannotAssignToImmutable(expr, message).addToHolder(holder)
    }
}
