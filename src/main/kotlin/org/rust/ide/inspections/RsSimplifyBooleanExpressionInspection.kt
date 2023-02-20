/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ide.inspections.fixes.SimplifyBooleanExpressionFix
import org.rust.ide.utils.BooleanExprSimplifier
import org.rust.ide.utils.isPure
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsVisitor

/**
 * Simplify pure boolean expressions
 */
class RsSimplifyBooleanExpressionInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Simplify boolean expression"

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {

        override fun visitExpr(expr: RsExpr) {
            if (expr.isPure() == true && BooleanExprSimplifier.canBeSimplified(expr)) {
                holder.registerProblem(expr, "Boolean expression can be simplified", SimplifyBooleanExpressionFix(expr))
            }
        }
    }
}
