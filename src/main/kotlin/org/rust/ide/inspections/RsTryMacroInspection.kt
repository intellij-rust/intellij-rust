/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.macroName

/**
 * Change `try!` macro to `?` operator.
 */
class RsTryMacroInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "try! macro usage"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitMacroExpr(o: RsMacroExpr) {
            if (o.macroCall.macroName != "try" || o.macroCall.tryMacroArgument == null) return
            holder.registerProblem(
                o,
                "try! macro can be replaced with ? operator",
                object : LocalQuickFix {
                    override fun getName() = "Change try! to ?"

                    override fun getFamilyName() = name

                    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                        val macro = descriptor.psiElement as RsMacroExpr
                        val body = macro.macroCall.tryMacroArgument!!.expr
                        val tryExpr = RsPsiFactory(project).createExpression("${body.text}?") as RsTryExpr
                        macro.replace(tryExpr)
                    }
                }
            )
        }
    }
}
