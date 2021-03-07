/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.psi.PsiElement
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.fixes.RemoveParameterFix
import org.rust.ide.inspections.fixes.RemoveVariableFix
import org.rust.ide.inspections.fixes.RenameFix
import org.rust.ide.utils.isCfgUnknown
import org.rust.lang.core.dfa.liveness.DeclarationKind
import org.rust.lang.core.dfa.liveness.DeclarationKind.Parameter
import org.rust.lang.core.dfa.liveness.DeclarationKind.Variable
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
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

                val reported = mutableSetOf<RsPatBinding>()

                for (deadDeclaration in liveness.deadDeclarations) {
                    val binding = deadDeclaration.binding
                    val name = binding.name ?: continue
                    if (name.startsWith("_")) continue
                    registerUnusedBindingProblem(holder, binding, name, deadDeclaration.kind)
                    reported.add(binding)
                }
                for (deadAssignment in liveness.deadAssignments) {
                    val (binding, element) = deadAssignment
                    val name = binding.name ?: continue
                    if (name.startsWith("_")) continue
                    if (binding in reported) continue
                    registerUnusedAssignmentProblem(holder, element, name)
                }
            }
        }

    private fun registerUnusedBindingProblem(
        holder: RsProblemsHolder,
        binding: RsPatBinding,
        name: String,
        kind: DeclarationKind
    ) {
        if (!binding.isPhysical) return

        if (binding.isCfgUnknown) return

        // TODO: remove this check when multi-resolve for `RsOrPat` is implemented
        if (binding.ancestorStrict<RsOrPat>() != null) return

        val isSimplePat = binding.topLevelPattern is RsPatIdent
        val message = if (isSimplePat) {
            when (kind) {
                Parameter -> "Parameter `$name` is never used"
                Variable -> "Variable `$name` is never used"
            }
        } else {
            "Binding `$name` is never used"
        }

        val fixes = mutableListOf<LocalQuickFix>(RenameFix(binding, "_$name"))
        if (isSimplePat) {
            when (kind) {
                Parameter -> fixes.add(RemoveParameterFix(binding, name))
                Variable -> fixes.add(RemoveVariableFix(binding, name))
            }
        }

        holder.registerLintProblem(binding, message, *fixes.toTypedArray())
    }

    private fun registerUnusedAssignmentProblem(
        holder: RsProblemsHolder,
        element: RsElement,
        name: String,
    ) {
        if (!element.isPhysical) return

        if (element.isCfgUnknown) return

        // TODO: remove this check when multi-resolve for `RsOrPat` is implemented
        if (element.ancestorStrict<RsOrPat>() != null) return

        val message = "value assigned to `$name` is never read"
        val fixes = mutableListOf<LocalQuickFix>() //TODO: add fixes
        holder.registerLintProblem(element, message, *fixes.toTypedArray())
    }
}
