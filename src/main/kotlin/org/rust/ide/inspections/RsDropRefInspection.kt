/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.ide.inspections.fixes.RemoveRefFix
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type

/**
 * Checks for calls to std::mem::drop with a reference instead of an owned value. Analogue of Clippy's drop_ref.
 * Quick fix: Use the owned value as the argument.
 */
class RsDropRefInspection : RsLocalInspectionTool() {
    override fun getDisplayName(): String = "Drop reference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitCallExpr(expr: RsCallExpr) = inspectExpr(expr, holder)
        }

    fun inspectExpr(expr: RsCallExpr, holder: ProblemsHolder) {
        val pathExpr = expr.expr as? RsPathExpr ?: return

        val resEl = pathExpr.path.reference.resolve()
        if (resEl !is RsFunction || resEl.crateRelativePath != "::mem::drop") return

        val args = expr.valueArgumentList.exprList
        if (args.size != 1) return

        val arg = args[0]
        if (arg.type is TyReference) {
            val fixes = if (arg.text[0] == '&') arrayOf(RemoveRefFix(arg, "Call with owned value")) else emptyArray()
            holder.registerProblem(
                expr,
                "Call to std::mem::drop with a reference argument. Dropping a reference does nothing",
                *fixes)
        }
    }
}
