/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.psi.ext.RsTraitOrImpl
import org.rust.lang.core.psi.ext.bounds
import org.rust.lang.core.psi.ext.flattenHierarchy
import org.rust.lang.core.psi.ext.resolveToBoundTrait
import org.rust.lang.core.resolve.asFunctionType
import org.rust.lang.core.resolve.isAnyFnTrait
import org.rust.lang.core.types.BoundElement

class TyTypeParameter private constructor(
    private val parameter: TypeParameter,
    private val name: String?,
    private val bounds: Collection<BoundElement<RsTraitItem>>
) : Ty {

    constructor(parameter: RsTypeParameter) : this(Named(parameter), parameter.name, bounds(parameter))
    constructor(trait: RsTraitOrImpl) : this(
        Self(trait),
        "Self",
        trait.implementedTrait?.let { listOf(it) } ?: emptyList()
    )
    constructor(trait: RsTraitItem, target: String) : this(
        AssociatedType(trait, target),
        "${trait.name}::$target",
        emptyList()
    )

    override fun equals(other: Any?): Boolean = other is TyTypeParameter && other.parameter == parameter
    override fun hashCode(): Int = parameter.hashCode()

    fun getTraitBoundsTransitively(): Collection<BoundElement<RsTraitItem>> =
        bounds.flatMap { it.flattenHierarchy }

    override fun canUnifyWith(other: Ty, project: Project, mapping: TypeMapping?): Boolean {
        if (mapping == null) return true

        if (other is TyFunction) {
            for (bound in bounds) {
                if (bound.element.isAnyFnTrait) {
                    val fnType = bound.asFunctionType
                    if (fnType != null && fnType.retType is TyTypeParameter) {
                        fnType.retType.canUnifyWith(other.retType, project, mapping)
                    }
                }
            }
        } else {
            val traits = findImplsAndTraits(project, other)
            for (bound in bounds) {
                val trait = traits.find { it.element.implementedTrait?.element == bound.element }
                if (trait != null) {
                    mapping.merge(bound.subst.reverse().substituteInValues(trait.subst))
                }
            }
        }

        mapping.merge(mapOf(this to other))
        return true
    }

    override fun substitute(subst: Substitution): Ty {
        val ty = subst[this] ?: TyUnknown
        return if (ty !is TyUnknown) ty else TyTypeParameter(parameter, name, bounds.map { it.substitute(subst) })
    }

    override fun toString(): String = name ?: "<unknown>"

    private interface TypeParameter
    private data class Named(val parameter: RsTypeParameter) : TypeParameter
    private data class Self(val trait: RsTraitOrImpl) : TypeParameter
    private data class AssociatedType(val trait: RsTraitItem, val target: String) : TypeParameter
}

private fun bounds(parameter: RsTypeParameter): List<BoundElement<RsTraitItem>> =
    parameter.bounds.mapNotNull { it.bound.traitRef?.resolveToBoundTrait }
