/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.RsBundle
import org.rust.ide.fixes.ReplaceIncDecOperatorFix
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
    override fun getDisplayName() = RsBundle.message("double.negation")

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitUnaryExpr(expr: RsUnaryExpr) {
                if (expr.isNegation && expr.expr.isNegation) {
                    val fixes = listOfNotNull(expr.minus?.let { ReplaceIncDecOperatorFix.create(it) }).toTypedArray()
                    holder.registerProblem(expr, RsBundle.message("inspection.message.x.could.be.misinterpreted.as.pre.decrement.but.effectively.no.op"), *fixes)
                }
            }
        }

    override val isSyntaxOnly: Boolean = true

    private val RsExpr?.isNegation: Boolean
        get() = this is RsUnaryExpr && minus != null
}
