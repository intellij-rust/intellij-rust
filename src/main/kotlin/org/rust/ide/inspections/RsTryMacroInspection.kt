package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsTryExpr
import org.rust.lang.core.psi.RsTryMacro
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.RustPsiFactory

/**
 * Change `try!` macro to `?` operator.
 */
class RsTryMacroInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "try! macro usage"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitTryMacro(o: RsTryMacro) = holder.registerProblem(
            o.macroInvocation,
            "try! macro can be replaced with ? operator",
            object : LocalQuickFix {
                override fun getName() = "Change try! to ?"

                override fun getFamilyName() = name

                override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                    val macro = descriptor.psiElement.parent as RsTryMacro
                    val body = macro.tryMacroArgs?.expr ?: return
                    val tryExpr = RustPsiFactory(project).createExpression("${body.text}?") as RsTryExpr
                    macro.replace(tryExpr)
                }
            }
        )
    }
}
