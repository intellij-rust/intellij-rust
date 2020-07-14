/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.usageAnalysis

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiElement
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.inspections.RsLintInspection
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.utils.isCfgUnknown
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.descendantsWithMacrosOfType
import org.rust.lang.core.psi.ext.expansion
import org.rust.lang.core.types.analysis.DeclarationKind
import org.rust.lang.core.types.analysis.ProblematicDeclaration

abstract class RsUsageAnalysisInspection : RsLintInspection() {
    /**
     * Declarations that are problematic (dead, unnecessarily mutable) in the given function.
     */
    abstract fun getProblematicDeclarations(function: RsFunction): List<ProblematicDeclaration>

    /**
     * Provide the error message for the given variable/parameter declaration.
     */
    abstract fun getErrorMessage(binding: RsPatBinding, name: String, kind: DeclarationKind): String

    /**
     * Provide quick fixes that should correct the given declaration.
     */
    abstract fun getFixes(binding: RsPatBinding, name: String, kind: DeclarationKind): List<LocalQuickFix>

    /**
     * Returns the element from the binding that should be highlighted with an error/warning.
     */
    open fun getElementToHighlight(binding: RsPatBinding): PsiElement? = binding

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitFunction(func: RsFunction) {
                // Disable inside doc tests
                if (func.isDoctestInjection) return

                // Don't analyze functions with unresolved macro calls
                if (func.descendantsWithMacrosOfType<RsMacroCall>().any { it.expansion == null }) return

                // Don't analyze functions with unresolved struct literals, e.g.:
                // let x = 1;
                // S { x }
                if (func.descendantsWithMacrosOfType<RsStructLiteral>().any { it.path.reference?.resolve() == null }) return

                for (deadDeclaration in getProblematicDeclarations(func)) {
                    val name = deadDeclaration.binding.name ?: continue
                    if (name.startsWith("_")) continue
                    registerDeclarationProblem(holder, deadDeclaration.binding, name, deadDeclaration.kind)
                }
            }
        }

    protected fun registerDeclarationProblem(
        holder: RsProblemsHolder,
        binding: RsPatBinding,
        name: String,
        kind: DeclarationKind
    ) {
        if (!binding.isPhysical) return

        if (binding.isCfgUnknown) return

        // TODO: remove this check when multi-resolve for `RsOrPat` is implemented
        if (binding.ancestorStrict<RsOrPat>() != null) return

        val message = getErrorMessage(binding, name, kind)
        val fixes = getFixes(binding, name, kind)

        getElementToHighlight(binding)?.let {
            holder.registerLintProblem(it, message, *fixes.toTypedArray())
        }
    }
}
