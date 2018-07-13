/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.HAS_TY_TYPE_PARAMETER_MASK
import org.rust.lang.core.types.type

class TyTypeParameter private constructor(
    val parameter: TypeParameter,
    val isSized: Boolean,
    boundsSupplier: () -> Collection<BoundElement<RsTraitItem>>
) : Ty(HAS_TY_TYPE_PARAMETER_MASK) {

    private val bounds: Collection<BoundElement<RsTraitItem>> by lazy(LazyThreadSafetyMode.NONE, boundsSupplier)

    override fun equals(other: Any?): Boolean = other is TyTypeParameter && other.parameter == parameter
    override fun hashCode(): Int = parameter.hashCode()

    fun getTraitBoundsTransitively(): Collection<BoundElement<RsTraitItem>> =
        bounds.flatMap { it.flattenHierarchy }

    val name: String? get() = parameter.name

    interface TypeParameter {
        val name: String?
    }

    object Self : TypeParameter {
        override val name: String? get() = "Self"
    }

    data class Named(val parameter: RsTypeParameter) : TypeParameter {
        override val name: String? get() = parameter.name
    }

    companion object {
        private val self = TyTypeParameter(Self, isSized = false) { emptyList() }

        fun self(): TyTypeParameter = self

        fun self(item: RsTraitOrImpl): TyTypeParameter {
            val isSized = when (item) {
                is RsTraitItem -> item.implementedTrait?.flattenHierarchy.orEmpty().any { it.element.isSizedTrait  }
                is RsImplItem -> item.typeReference?.type?.isSized() == true
                else -> error("item must be instance of `RsTraitItem` or `RsImplItem`")
            }
            return TyTypeParameter(Self, isSized) { listOfNotNull(item.implementedTrait) }
        }

        fun named(parameter: RsTypeParameter): TyTypeParameter =
            TyTypeParameter(Named(parameter), parameter.isSized) { bounds(parameter) }
    }
}

private fun bounds(parameter: RsTypeParameter): List<BoundElement<RsTraitItem>> =
    parameter.bounds.mapNotNull {
        val trait = it.bound.traitRef?.resolveToBoundTrait ?: return@mapNotNull null
        // if `T: ?Sized` then T doesn't have `Sized` bound
        if (!trait.element.isSizedTrait || parameter.isSized) trait else null
    }
