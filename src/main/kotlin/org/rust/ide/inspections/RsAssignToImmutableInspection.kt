/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ide.annotator.fixes.AddMutableFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.containerExpr
import org.rust.lang.core.psi.ext.isAssignBinaryExpr
import org.rust.lang.core.psi.ext.isDereference
import org.rust.lang.core.psi.ext.unwrapParenExprs
import org.rust.lang.core.types.isImmutable
import org.rust.lang.core.types.ty.TyPointer
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsAssignToImmutableInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitBinaryExpr(expr: RsBinaryExpr) {
                if (expr.isAssignBinaryExpr) checkAssignment(expr, holder)
            }
        }

    private fun checkAssignment(expr: RsBinaryExpr, holder: RsProblemsHolder) {
        val left = unwrapParenExprs(expr.left).takeIf { it.isImmutable } ?: return

        when (left) {
            is RsDotExpr -> registerProblem(holder, "field of immutable binding", expr, left.expr)
            is RsIndexExpr -> registerProblem(holder, "indexed content of immutable binding", expr, left.containerExpr)
            is RsUnaryExpr -> if (left.isDereference) registerDereferenceProblem(left, holder, expr)
        }
    }

    private fun registerDereferenceProblem(left: RsUnaryExpr, holder: RsProblemsHolder, expr: RsBinaryExpr) {
        when (left.expr?.type) {
            is TyReference -> registerProblem(holder, "immutable borrowed content", expr)
            is TyPointer -> registerProblem(holder, "immutable dereference of raw pointer", expr)
        }
    }

    private fun registerProblem(holder: RsProblemsHolder, message: String, expr: RsExpr, assigneeExpr: RsExpr? = null) {
        val fix = assigneeExpr?.let { AddMutableFix.createIfCompatible(it) }
        RsDiagnostic.CannotAssignToImmutable(expr, message, fix).addToHolder(holder)
    }
}
