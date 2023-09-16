/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.fixes.RsQuickFixBase
import org.rust.lang.core.psi.RsFormatMacroArgument
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.macroName

/**
 * Replace `println!("")` with `println!()` available since Rust 1.14.0
 */
class RsSimplifyPrintInspection : RsLocalInspectionTool() {

    @Suppress("DialogTitleCapitalization")
    override fun getDisplayName(): String = RsBundle.message("println.usage")

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {

        override fun visitMacroCall2(o: RsMacroCall) {
            val macroName = o.macroName
            val formatMacroArg = o.formatMacroArgument ?: return
            if (!(macroName.endsWith("println"))) return

            if (emptyStringArg(formatMacroArg) == null) return
            holder.registerProblem(
                o,
                RsBundle.message("inspection.message.println.macro.invocation.can.be.simplified"),
                RemoveUnnecessaryPrintlnArgument(o)
            )
        }
    }

    private class RemoveUnnecessaryPrintlnArgument(element: RsMacroCall) : RsQuickFixBase<RsMacroCall>(element) {
        override fun getText() = RsBundle.message("intention.name.remove.unnecessary.argument")
        override fun getFamilyName() = name

        override fun invoke(project: Project, editor: Editor?, element: RsMacroCall) {
            val arg = emptyStringArg(element.formatMacroArgument!!) ?: return
            arg.delete()
        }
    }
}

private fun emptyStringArg(arg: RsFormatMacroArgument): PsiElement? {
    val singeArg = arg.formatMacroArgList.singleOrNull() ?: return null
    if (singeArg.text != "\"\"") return null
    return singeArg
}
