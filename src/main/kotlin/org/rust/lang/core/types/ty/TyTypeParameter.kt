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
    constructor(type: Ty, trait: RsTraitItem, target: String) : this(
        AssociatedType(type, trait, target),
        "<$type as ${trait.name}>::$target",
        emptyList()
    )
    constructor(trait: RsTraitItem, target: String) : this(TyTypeParameter(trait), trait, target)

    override fun equals(other: Any?): Boolean = other is TyTypeParameter && other.parameter == parameter
    override fun hashCode(): Int = parameter.hashCode()

    fun getTraitBoundsTransitively(): Collection<BoundElement<RsTraitItem>> =
        bounds.flatMap { it.flattenHierarchy }.map { it.substitute(mapOf(TyTypeParameter(it.element) to this)) }

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
            for ((element, boundSubst) in bounds) {
                val trait = traits.find { it.element.implementedTrait?.element == element }
                if (trait != null) {
                    val subst = boundSubst.substituteInValues(mapOf(TyTypeParameter(element) to this))
                    for ((k, v) in subst) {
                        trait.subst[k]?.let { v.canUnifyWith(it, project, mapping) }
                    }
                }
            }
        }

        mapping.merge(mapOf(this to other))
        return true
    }

    override fun substitute(subst: Substitution): Ty {
        val ty = subst[this] ?: TyUnknown
        if (ty !is TyUnknown) return ty
        if (parameter is AssociatedType) {
            return TyTypeParameter(parameter.type.substitute(subst), parameter.trait, parameter.target)
        }
        return TyTypeParameter(parameter, name, bounds.map { it.substitute(subst) })
    }

    override fun toString(): String = name ?: "<unknown>"

    private interface TypeParameter
    private data class Named(val parameter: RsTypeParameter) : TypeParameter
    private data class Self(val trait: RsTraitOrImpl) : TypeParameter
    private data class AssociatedType(val type: Ty, val trait: RsTraitItem, val target: String) : TypeParameter
}

private fun bounds(parameter: RsTypeParameter): List<BoundElement<RsTraitItem>> =
    parameter.bounds.mapNotNull { it.bound.traitRef?.resolveToBoundTrait }
