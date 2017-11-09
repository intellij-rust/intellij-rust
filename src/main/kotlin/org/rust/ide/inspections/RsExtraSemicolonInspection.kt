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
import org.rust.lang.core.ExitPoint
import org.rust.lang.core.psi.RsExprStmt
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type

/**
 * Suggest to remove a semicolon in situations like
 *
 * ```
 * fn foo() -> i32 { 92; }
 * ```
 */
class RsExtraSemicolonInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Extra semicolon"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RsVisitor() {
            override fun visitFunction(o: RsFunction) = inspect(holder, o)
        }
}


private fun inspect(holder: ProblemsHolder, fn: RsFunction) {
    val retType = fn.retType?.typeReference ?: return
    if (retType.type == TyUnit) return
    ExitPoint.process(fn) { exitPoint ->
        when (exitPoint) {
            is ExitPoint.TailStatement -> {
                holder.registerProblem(
                    exitPoint.stmt,
                    "Function returns () instead of ${retType.text}",
                    object : LocalQuickFix {
                        override fun getName() = "Remove semicolon"

                        override fun getFamilyName() = name

                        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                            val statement = (descriptor.psiElement as RsExprStmt)
                            statement.replace(statement.expr)
                        }
                    }
                )
            }
        }
    }
}
