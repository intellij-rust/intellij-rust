/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.resolveToMacro
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.psi.kind

class RsCompileErrorMacroInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsWithMacrosInspectionVisitor() {
        override fun visitMacroCall2(o: RsMacroCall) {
            val resolvedTo = o.resolveToMacro() ?: return
            if (resolvedTo.name != "compile_error" || resolvedTo.containingCrate.origin != PackageOrigin.STDLIB) return
            val macroArgument = o.macroArgument ?: return
            val messageLiteral = macroArgument.litExprList.singleOrNull() ?: return
            val message = (messageLiteral.kind as? RsLiteralKind.String)?.value ?: return
            val errorRange = o.path.textRange.union(macroArgument.textRange).shiftLeft(o.startOffset)
            holder.registerProblem(o, errorRange, message, alwaysShowInMacros = true)
        }
    }
}
