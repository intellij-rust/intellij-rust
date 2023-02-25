/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.ide.utils.skipParenExprDown
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.endOffsetInParent

/**
 * Change `while true` to `loop`.
 */
class RsWhileTrueLoopInspection : RsLintInspection() {
    override fun getDisplayName(): String = "While true loop"
    override fun getLint(element: PsiElement): RsLint = RsLint.WhileTrue

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        override fun visitWhileExpr(o: RsWhileExpr) {
            val condition = o.condition ?: return
            val expr = condition.skipParenExprDown() as? RsLitExpr ?: return
            if (o.block == null) return
            if (expr.textMatches("true")) {
                holder.registerLintProblem(
                    o,
                    "Denote infinite loops with `loop { ... }`",
                    TextRange.create(o.`while`.startOffsetInParent, condition.endOffsetInParent),
                    RsLintHighlightingType.WEAK_WARNING,
                    listOf(UseLoopFix())
                )
            }
        }
    }

    override val isSyntaxOnly: Boolean = true

    private class UseLoopFix : LocalQuickFix {
        override fun getFamilyName(): String = "Use `loop`"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement as? RsWhileExpr ?: return
            val block = element.block ?: return
            val label = element.labelDecl?.text ?: ""
            val loopExpr = RsPsiFactory(project).createExpression("${label}loop ${block.text}") as RsLoopExpr
            element.replace(loopExpr)
        }
    }
}
