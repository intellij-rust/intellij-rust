/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.RsStructLiteralField
import org.rust.lang.core.psi.RsVisitor


class RsFieldInitShorthandInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitStructLiteralField(o: RsStructLiteralField) {
            val init = o.expr ?: return
            if (!(init is RsPathExpr && init.text == o.identifier.text)) return
            holder.registerProblem(
                o,
                "Expression can be simplified",
                ProblemHighlightType.WEAK_WARNING,
                object : LocalQuickFix {
                    override fun getFamilyName(): String = "Use initialization shorthand"

                    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                        val field = descriptor.psiElement as RsStructLiteralField
                        field.expr?.delete()
                        field.colon?.delete()
                    }
                }
            )
        }
    }
}
