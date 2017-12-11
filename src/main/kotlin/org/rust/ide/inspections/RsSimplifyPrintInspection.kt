/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFormatMacroArgument
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.macroName

/**
 * Replace `println!("")` with `println!()` available since Rust 1.14.0
 */
class RsSimplifyPrintInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "println!(\"\") usage"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {

        override fun visitMacroCall(o: RsMacroCall) {
            val macroName = o.macroName
            val formatMacroArg = o.formatMacroArgument ?: return
            if (!(macroName.endsWith("println"))) return

            if (emptyStringArg(formatMacroArg) == null) return
            holder.registerProblem(
                o,
                "println! macro invocation can be simplified",
                object : LocalQuickFix {
                    override fun getName() = "Remove unnecessary argument"

                    override fun getFamilyName() = name

                    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                        val macro = descriptor.psiElement as RsMacroCall
                        val arg = emptyStringArg(macro.formatMacroArgument!!) ?: return
                        arg.delete()
                    }
                }
            )
        }
    }

    private fun emptyStringArg(arg: RsFormatMacroArgument): PsiElement? {
        val singeArg = arg.formatMacroArgList.singleOrNull() ?: return null
        if (singeArg.text != "\"\"") return null
        return singeArg
    }
}
