/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.rust.ide.utils.skipParenExprDown
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.endOffsetInParent

/**
 * Change `while true` to `loop`.
 */
class RsWhileTrueLoopInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "While true loop"

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitWhileExpr(o: RsWhileExpr) {
            val condition = o.condition ?: return
            val expr = condition.skipParenExprDown() as? RsLitExpr ?: return
            val block = o.block ?: return
            val label = o.labelDecl?.text ?: ""
            if (expr.textMatches("true")) {
                holder.registerProblem(
                    o,
                    TextRange.create(o.`while`.startOffsetInParent, condition.endOffsetInParent),
                    "Denote infinite loops with `loop { ... }`",
                    object : LocalQuickFix {
                        override fun getName() = "Use `loop`"

                        override fun getFamilyName() = name

                        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                            val loopExpr = RsPsiFactory(project).createExpression("${label}loop ${block.text}") as RsLoopExpr
                            o.replace(loopExpr)
                        }
                    }
                )
            }

        }
    }
}
