/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.rust.lang.core.macros.isExprOrStmtContext
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTryExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.macroName
import org.rust.lang.core.psi.ext.replaceWithExpr

/**
 * Change `try!` macro to `?` operator.
 */
class RsTryMacroInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "try! macro usage"

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMacroCall(o: RsMacroCall) {
            val isApplicable = o.isExprOrStmtContext && o.macroName == "try" && o.exprMacroArgument?.expr != null
            if (!isApplicable) return
            holder.registerProblem(
                o,
                "try! macro can be replaced with ? operator",
                object : LocalQuickFix {
                    override fun getName() = "Change try! to ?"

                    override fun getFamilyName() = name

                    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                        val macro = descriptor.psiElement as? RsMacroCall ?: return
                        val body = macro.exprMacroArgument?.expr ?: return
                        val tryExpr = RsPsiFactory(project).createExpression("()?") as RsTryExpr
                        tryExpr.expr.replace(body.copy())
                        macro.replaceWithExpr(tryExpr)
                    }
                }
            )
        }
    }
}
