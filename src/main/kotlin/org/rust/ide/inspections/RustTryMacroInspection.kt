package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustElementVisitor
import org.rust.lang.core.psi.RustTryExprElement
import org.rust.lang.core.psi.RustTryMacroElement

/**
 * Change `try!` macro to `?` operator.
 */
class RustTryMacroInspection : RustLocalInspectionTool() {
    override fun getDisplayName() = "try! macro usage"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RustElementVisitor() {
        override fun visitTryMacro(o: RustTryMacroElement) = holder.registerProblem(
            o.macroInvocation,
            "try! macro can be replaced with ? operator",
            object : LocalQuickFix {
                override fun getName() = "Change try! to ?"

                override fun getFamilyName() = name

                override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                    val macro = descriptor.psiElement.parent as RustTryMacroElement
                    val body = macro.tryMacroArgs?.expr ?: return
                    val tryExpr = RustElementFactory.createExpression(project, "${body.text}?") as RustTryExprElement
                    macro.replace(tryExpr)
                }
            }
        )
    }
}
