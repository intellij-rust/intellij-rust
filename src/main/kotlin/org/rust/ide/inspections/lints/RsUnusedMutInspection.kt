/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.psi.PsiElement
import com.intellij.psi.util.contextOfType
import org.rust.RsBundle
import org.rust.ide.fixes.RemoveElementFix
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsUnusedMutInspection : RsLintInspection() {
    override fun getDisplayName(): String = RsBundle.message("no.mutable.required")
    override fun getLint(element: PsiElement): RsLint = RsLint.UnusedMut

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitBindingMode(o: RsBindingMode) {
                val patBinding = o.parent as? RsPatBinding ?: return
                if (!patBinding.mutability.isMut) return
                if (patBinding.isDoctestInjection) {
                    return
                }

                if (patBinding.searchReferencesAfterExpansion()
                        .any { checkOccurrenceNeedMutable(it.element.parent) }) return

                val mut = o.mut ?: return
                holder.registerLintProblem(
                    mut,
                    RsBundle.message("inspection.message.unused.mut"),
                    RsLintHighlightingType.UNUSED_SYMBOL,
                    listOf(RemoveElementFix(mut)),
                )
            }
        }

    fun checkOccurrenceNeedMutable(occurrence: PsiElement): Boolean {
        when (val parent = occurrence.parent) {
            is RsUnaryExpr -> return parent.isMutable || parent.mul != null
            is RsBinaryExpr -> return parent.left == occurrence
            is RsMethodCall -> {
                val ref = parent.reference.resolve() as? RsFunction ?: return true
                val self = ref.selfParameter ?: return true
                return self.mutability.isMut
            }
            is RsTupleExpr -> {
                val expr = parent.parent as? RsUnaryExpr ?: return true
                return expr.isMutable
            }
            is RsValueArgumentList -> return false
            is RsExprMacroArgument, is RsVecMacroArgument, is RsAssertMacroArgument, is RsConcatMacroArgument, is RsEnvMacroArgument -> return false
            is RsFormatMacroArg-> {
                val argList = parent.contextOfType<RsFormatMacroArgument>() ?: return true
                val macroCall = argList.contextOfType<RsMacroCall>() ?: return true
                val macroName = macroCall.macroName
                val isWriteMacro = macroName == "write" || macroName == "writeln"
                if (!isWriteMacro) return false
                return argList.formatMacroArgList.firstOrNull() == parent
            }
        }
        return true
    }

    private val RsUnaryExpr.isMutable: Boolean get() = mut != null
}
