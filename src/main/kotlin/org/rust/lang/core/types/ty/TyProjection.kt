/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.psi.ext.withDefaultSubst
import org.rust.lang.core.types.*
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

/**
 * Represents projection of an associated type.
 * For example, return types of the following functions `foo` and `bar` is [TyProjection]:
 * ```
 * trait Trait { type Item; }
 * fn foo<T: Trait>() -> T::Item { unimplemented!() }
 * fn bar<T: Trait>() -> <T as Trait>::Item { unimplemented!() }
 * ```
 *
 * Fields meaning:
 * ```
 * <T as Trait>::Item
 *  |    |       |
 *  type trait   target
 *
 * ```
 */
@Suppress("DataClassPrivateConstructor")
data class TyProjection private constructor(
    val type: Ty,
    val trait: BoundElement<RsTraitItem>,
    val target: BoundElement<RsTypeAlias>
) : Ty(type.flags or mergeElementFlags(trait) or mergeElementFlags(target) or HAS_TY_PROJECTION_MASK) {

    /**
     * Extracts the underlying trait reference from this projection.
     * For example, if this is a projection of `<T as Iterator>::Item`,
     * then this property would return a `T: Iterator` trait reference.
     */
    val traitRef: TraitRef
        get() = TraitRef(type, trait)

    val typeArguments: List<Ty>
        get() = target.element.typeParameters.map { typeParameterValues[it] ?: TyUnknown }

    override val typeParameterValues: Substitution
        get() = target.subst

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyProjection(type.foldWith(folder), trait.foldWith(folder), target.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        type.visitWith(visitor) || trait.visitWith(visitor) || target.visitWith(visitor)

    companion object {
        fun valueOf(type: Ty, trait: BoundElement<RsTraitItem>, target: BoundElement<RsTypeAlias>): TyProjection {
            check(trait.element == (target.element.owner as? RsAbstractableOwner.Trait)?.trait)
            return TyProjection(type, trait, target)
        }

        fun valueOf(type: Ty, target: BoundElement<RsTypeAlias>): TyProjection {
            val trait = (target.element.owner as? RsAbstractableOwner.Trait)?.trait?.withDefaultSubst()
                ?: error("Tried to construct an associated type from RsTypeAlias declared out of a trait")
            return valueOf(type, trait, target)
        }

        fun valueOf(target: BoundElement<RsTypeAlias>): TyProjection =
            valueOf(TyTypeParameter.self(), target)
    }
}
