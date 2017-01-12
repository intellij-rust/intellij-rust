package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFormatLikeMacro
import org.rust.lang.core.psi.RsVisitor

/**
 * Replace `println!("")` with `println!()` available since Rust 1.14.0
 */
class RsSimplifyPrintInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "println!(\"\") usage"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {

        override fun visitFormatLikeMacro(o: RsFormatLikeMacro) {
            if (emptyStringArg(o) == null) return
            holder.registerProblem(
                o,
                "println! macro invocation can be simplified",
                object : LocalQuickFix {
                    override fun getName() = "Remove unnecessary argument"

                    override fun getFamilyName() = name

                    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                        val macro = descriptor.psiElement as RsFormatLikeMacro
                        val arg = emptyStringArg(macro) ?: return
                        arg.delete()
                    }
                }
            )
        }
    }

    private fun emptyStringArg(macro: RsFormatLikeMacro): PsiElement? {
        if (!macro.macroInvocation.text.startsWith("println")) return null
        val arg = macro.formatMacroArgs?.formatMacroArgList.orEmpty().singleOrNull()
            ?: return null
        if (arg.text != "\"\"") return null
        return arg
    }
}
