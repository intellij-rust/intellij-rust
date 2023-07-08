/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.RsBundle
import org.rust.ide.fixes.ChangeTryMacroToTryOperator
import org.rust.lang.core.macros.isExprOrStmtContext
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.isStdTryMacro

/**
 * Change `try!` macro to `?` operator.
 */
class RsTryMacroInspection : RsLocalInspectionTool() {

    @Suppress("DialogTitleCapitalization")
    override fun getDisplayName(): String = RsBundle.message("try.macro.usage")

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        override fun visitMacroCall2(o: RsMacroCall) {
            val isApplicable = o.isExprOrStmtContext && o.isStdTryMacro
            if (!isApplicable) return
            holder.registerProblem(
                o,
                RsBundle.message("inspection.message.try.macro.can.be.replaced.with.operator"),
                ChangeTryMacroToTryOperator(o)
            )
        }
    }
}

