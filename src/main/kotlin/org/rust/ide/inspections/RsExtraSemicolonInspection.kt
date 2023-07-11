/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.RsBundle
import org.rust.ide.fixes.RemoveSemicolonFix
import org.rust.lang.core.dfa.ExitPoint
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.ty.TyUnit

/**
 * Suggest to remove a semicolon in situations like
 *
 * ```
 * fn foo() -> i32 { 92; }
 * ```
 */
class RsExtraSemicolonInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = RsBundle.message("extra.semicolon")

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitFunction2(o: RsFunction) {
                val retType = o.retType?.typeReference ?: return
                if (retType.normType is TyUnit) return
                ExitPoint.process(o) { exitPoint ->
                    if (exitPoint is ExitPoint.InvalidTailStatement) {
                        holder.registerProblem(
                            exitPoint.stmt,
                            RsBundle.message("inspection.message.function.returns.instead", retType.text),
                            RemoveSemicolonFix(exitPoint.stmt)
                        )
                    }
                }
            }
        }
}

