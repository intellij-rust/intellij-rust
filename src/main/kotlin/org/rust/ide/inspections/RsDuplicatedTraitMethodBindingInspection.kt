/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ide.refactoring.findBinding
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.owner

class RsDuplicatedTraitMethodBindingInspection : RsLocalInspectionTool() {
    override fun getDisplayName() = "Duplicated trait method parameter binding"

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
            override fun visitFunction2(function: RsFunction) {
                if (function.owner !is RsAbstractableOwner.Trait) return
                if (!function.isAbstract) return

                val parameters = function.valueParameterList ?: return
                val bindings = mutableMapOf<String, MutableSet<RsPatBinding>>()
                parameters.valueParameterList.forEach {
                    val binding = it.pat?.findBinding() ?: return@forEach
                    val name = binding.name ?: return@forEach
                    val set = bindings.getOrPut(name) { mutableSetOf() }
                    set.add(binding)
                }

                bindings
                    .filter { it.value.size > 1 }
                    .forEach { (_, bindings) ->
                        bindings.forEach { binding ->
                            holder.registerProblem(
                                binding,
                                "Duplicated parameter name `${binding.name}`. Consider renaming it"
                            )
                        }
                    }
            }
        }
}
