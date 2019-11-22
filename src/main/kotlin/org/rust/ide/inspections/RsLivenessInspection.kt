/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import org.rust.ide.colors.RsColor
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.inspections.fixes.RenameFix
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.resolveToMacro
import org.rust.lang.core.types.DeclarationKind
import org.rust.lang.core.types.DeclarationKind.Parameter
import org.rust.lang.core.types.DeclarationKind.Variable
import org.rust.lang.core.types.liveness

class RsLivenessInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitFunction(func: RsFunction) {
                // Disable inside doc tests
                if (func.isDoctestInjection) return

                // Don't analyze functions with unresolved macro calls
                val macroCalls = func.block?.macroCallList ?: return
                if (macroCalls.any { it.resolveToMacro() == null }) return

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
        val message = when (kind) {
            Parameter -> "Parameter `$name` is never used"
            Variable -> "Variable `$name` is never used"
        }
        val descriptor = holder.manager.createProblemDescriptor(
            binding,
            binding,
            message,
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            holder.isOnTheFly,
            RenameFix(binding, "_$name")
        )
        descriptor.setTextAttributes(RsColor.DEAD_CODE.textAttributesKey)
        holder.registerProblem(descriptor)
    }
}
