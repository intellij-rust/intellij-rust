/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RsMacroExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTryExpr
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.macroName

/** Change `try!` macro to `?` operator. */
class RsTryMacroInspection : RsLocalInspectionTool() {

    override fun getDisplayName(): String = "try! macro usage"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RsVisitor() {
            override fun visitMacroExpr(macroExpr: RsMacroExpr) {
                if (macroExpr.macroCall.macroName != "try" || macroExpr.macroCall.tryMacroArgument == null) return
                holder.registerProblem(
                    macroExpr,
                    "try! macro can be replaced with ? operator",
                    object : LocalQuickFix {

                        override fun getName(): String = "Change try! to ?"

                        override fun getFamilyName(): String = name

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
