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
import org.rust.ide.utils.canBeSimplified
import org.rust.ide.utils.isPure
import org.rust.ide.utils.simplifyBooleanExpression
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsVisitor

/** Simplify pure boolean expressions. */
class RsSimplifyBooleanExpressionInspection : RsLocalInspectionTool() {

    override fun getDisplayName(): String = "Simplify boolean expression"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : RsVisitor() {
            override fun visitExpr(expr: RsExpr) {
                if (expr.isPure() != true) return
                val result = expr.canBeSimplified()
                if (!result) return
                holder.registerProblem(
                    expr,
                    "Boolean expression can be simplified",
                    object : LocalQuickFix {

                        override fun getName(): String = "Simplify boolean expression"

                        override fun getFamilyName(): String = name

                        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                            val expression = descriptor.psiElement as RsExpr
                            if (expression.isPure() != true) return
                            val (simplifiedExpr, simplificationResult) = expression.simplifyBooleanExpression()
                            if (!simplificationResult) return
                            expression.replace(simplifiedExpr)
                        }
                    }
                )
            }
        }
}
