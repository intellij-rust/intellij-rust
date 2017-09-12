/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.substitute

class TyTypeParameter private constructor(
    val parameter: TypeParameter,
    private val bounds: Collection<BoundElement<RsTraitItem>>
) : Ty {

    override fun equals(other: Any?): Boolean = other is TyTypeParameter && other.parameter == parameter
    override fun hashCode(): Int = parameter.hashCode()

    fun getTraitBoundsTransitively(): Collection<BoundElement<RsTraitItem>> =
        bounds.flatMap { it.flattenHierarchy }.map { it.substitute(mapOf(self() to this)) }

    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult {
        var result: UnifyResult = UnifyResult.exact

        if (!bounds.isEmpty()) {
            val traits = lookup.findImplsAndTraits(other)
            for ((element, boundSubst) in bounds) {
                val trait = traits.find { it.element.implementedTrait?.element == element }
                if (trait != null) {
                    val subst = boundSubst.substituteInValues(mapOf(self() to this))
                    for ((k, v) in subst) {
                        trait.subst[k]?.let { result = result.merge(v.unifyWith(it, lookup)) }
                    }
                } else {
                    result = result.merge(UnifyResult.fuzzy)
                }
            }
        }

        return result.merge(mapOf(this to other))
    }

    // TODO complete dismantle substitution and migrate to folding
    fun substituteOld(subst: Substitution): Ty {
        val ty = subst[this] ?: TyUnknown
        if (ty !is TyUnknown) return ty
        if (parameter is AssociatedType) {
            val oldType = parameter.type
            val newType = if (oldType is TyTypeParameter && oldType.parameter is Self) {
                oldType.substitute(subst)
            } else {
                oldType
            }
            return associated(newType, parameter.trait, parameter.target)
        }
        return TyTypeParameter(parameter, bounds.map { it.substitute(subst) })
    }

    override fun superFoldWith(folder: TypeFolder): Ty {
        if (parameter is AssociatedType) {
            return associated(parameter.type.foldWith(folder), parameter.trait, parameter.target)
        }
        return super.superFoldWith(folder)
    }

    val name: String? get() = parameter.name

    override fun toString(): String = tyToString(this)

    interface TypeParameter {
        val name: String?
    }
    private object Self : TypeParameter {
        override val name: String? get() = "Self"
    }

    private data class Named(val parameter: RsTypeParameter) : TypeParameter {
        override val name: String? get() = parameter.name
    }

    // TODO should be a separate Ty
    data class AssociatedType(val type: Ty, val trait: RsTraitItem, val target: RsTypeAlias) : TypeParameter {
        override val name: String? get() = "<$type as ${trait.name}>::${target.name}"
    }

    companion object {
        private val SELF = TyTypeParameter(Self, emptyList())

        fun self(): TyTypeParameter = SELF

        fun self(trait: RsTraitOrImpl): TyTypeParameter =
            TyTypeParameter(Self, trait.implementedTrait?.let { listOf(it) } ?: emptyList())

        fun named(parameter: RsTypeParameter): TyTypeParameter =
            TyTypeParameter(Named(parameter), bounds(parameter))

        fun associated(target: RsTypeAlias): TyTypeParameter =
            associated(self(), target)

        private fun associated(type: Ty, target: RsTypeAlias): TyTypeParameter {
            val trait = target.parentOfType<RsTraitItem>()
                ?: error("Tried to construct an associated type from RsTypeAlias declared out of a trait")
            return associated(type, trait, target)
        }

         fun associated(type: Ty, trait: RsTraitItem, target: RsTypeAlias): TyTypeParameter {
            return TyTypeParameter(
                AssociatedType(type, trait, target),
                emptyList())
        }
    }
}

private fun bounds(parameter: RsTypeParameter): List<BoundElement<RsTraitItem>> =
    parameter.bounds.mapNotNull { it.bound.traitRef?.resolveToBoundTrait }
