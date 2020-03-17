/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.inspections.fixes.RenameFix
import org.rust.ide.utils.isCfgUnknown
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.descendantsWithMacrosOfType
import org.rust.lang.core.psi.ext.expansion
import org.rust.lang.core.types.DeclarationKind
import org.rust.lang.core.types.DeclarationKind.Parameter
import org.rust.lang.core.types.DeclarationKind.Variable
import org.rust.lang.core.types.liveness

class RsLivenessInspection : RsLintInspection() {

    override fun getLint(element: PsiElement): RsLint? =
        RsLint.UnusedVariables

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

                val liveness = func.liveness ?: return

                for (deadDeclaration in liveness.deadDeclarations) {
                    val name = deadDeclaration.binding.name ?: continue
                    if (name.startsWith("_")) continue
                    registerUnusedProblem(holder, deadDeclaration.binding, name, deadDeclaration.kind)
                }
            }
        }

    private fun registerUnusedProblem(
        holder: RsProblemsHolder,
        binding: RsPatBinding,
        name: String,
        kind: DeclarationKind
    ) {
        if (!binding.isPhysical) return

        if (binding.isCfgUnknown) return

        // TODO: remove this check when multi-resolve for `RsOrPats` is implemented
        if (binding.ancestorStrict<RsOrPats>() != null) return

        val message = when (kind) {
            Parameter -> "Parameter `$name` is never used"
            Variable -> "Variable `$name` is never used"
        }

        holder.registerProblem(binding, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL, RenameFix(binding, "_$name"))
    }
}
