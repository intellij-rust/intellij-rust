/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsUnaryExpr
import org.rust.lang.core.psi.RsVisitor

/**
 * Checks for usage of double negation, which is a no-op in Rust but might be misleading for
 * programmers with background in languages that have prefix operators.
 *
 * Analogue of Clippy's double_neg.
 */
class RsDoubleNegInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Double negation"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitUnaryExpr(expr: RsUnaryExpr) {
                if (expr.isNegation && expr.expr.isNegation) {
                    holder.registerProblem(expr, "--x could be misinterpreted as a pre-decrement, but effectively is a no-op")
                }
            }
        }

    private val RsExpr?.isNegation: Boolean
        get() = this is RsUnaryExpr && minus != null
}
