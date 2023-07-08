/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import org.rust.RsBundle
import org.rust.ide.fixes.RemoveRefFix
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type

/**
 * Checks for calls to std::mem::drop with a reference instead of an owned value. Analogue of Clippy's drop_ref.
 * Quick fix: Use the owned value as the argument.
 */
class RsDropRefInspection : RsLocalInspectionTool() {
    override fun getDisplayName(): String = RsBundle.message("drop.reference")

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitCallExpr(expr: RsCallExpr) = inspectExpr(expr, holder)
        }

    fun inspectExpr(expr: RsCallExpr, holder: RsProblemsHolder) {
        val pathExpr = expr.expr as? RsPathExpr ?: return

        val fn = pathExpr.path.reference?.resolve() ?: return
        if (fn != expr.knownItems.drop) return

        val args = expr.valueArgumentList.exprList
        val arg = args.singleOrNull() ?: return

        if (arg.type is TyReference) {
            val fixes = RemoveRefFix.createIfCompatible(arg)?.let { arrayOf(it) } ?: LocalQuickFix.EMPTY_ARRAY
            holder.registerProblem(
                expr,
                RsBundle.message("inspection.message.call.to.std.mem.drop.with.reference.argument.dropping.reference.does.nothing"),
                *fixes)
        }
    }
}
