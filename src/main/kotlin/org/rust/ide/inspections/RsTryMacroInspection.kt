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
import org.rust.lang.core.psi.ext.isStdTryMacro
import org.rust.lang.core.psi.ext.macroBody
import org.rust.lang.core.psi.ext.replaceWithExpr

/**
 * Change `try!` macro to `?` operator.
 */
class RsTryMacroInspection : RsLocalInspectionTool() {

    @Suppress("DialogTitleCapitalization")
    override fun getDisplayName(): String = "try! macro usage"

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        override fun visitMacroCall2(o: RsMacroCall) {
            val isApplicable = o.isExprOrStmtContext && o.isStdTryMacro
            if (!isApplicable) return
            holder.registerProblem(
                o,
                "try! macro can be replaced with ? operator",
                object : LocalQuickFix {
                    override fun getName() = "Change try! to ?"

                    override fun getFamilyName() = name

                    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                        val macro = descriptor.psiElement as? RsMacroCall ?: return
                        val factory = RsPsiFactory(project)
                        val body = macro.macroBody ?: return
                        val expr = factory.tryCreateExpression(body) ?: return
                        val tryExpr = factory.createExpression("()?") as RsTryExpr
                        tryExpr.expr.replace(expr)
                        macro.replaceWithExpr(tryExpr)
                    }
                }
            )
        }
    }
}
