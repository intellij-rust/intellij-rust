/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ide.annotator.fixes.AddMutableFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.isAssignBinaryExpr
import org.rust.lang.core.types.declaration
import org.rust.lang.core.types.isImmutable
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsReassignImmutableInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitBinaryExpr(expr: RsBinaryExpr) {
                val left = expr.left.takeIf { it.isImmutable } ?: return

                if (expr.isAssignBinaryExpr && left is RsPathExpr) {
                    // TODO: perform some kind of data-flow analysis
                    val letExpr = left.declaration?.ancestorStrict<RsLetDecl>()
                    if (letExpr == null) registerProblem(holder, expr, left)

                    // this brings false-negative, because it doesn't check initialization properly:
                    // let x; x = 1;
                    // x = 2; <-- reassignment of immutable `x`, but the problem did not register
                    else if (letExpr.eq != null) registerProblem(holder, expr, left)
                }
            }
        }

    private fun registerProblem(holder: RsProblemsHolder, expr: RsExpr, nameExpr: RsExpr) {
        val fix = AddMutableFix.createIfCompatible(nameExpr)
        RsDiagnostic.CannotReassignToImmutable(expr, fix).addToHolder(holder)
    }

}
