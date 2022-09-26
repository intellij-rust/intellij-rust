/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.flattenHierarchy
import org.rust.lang.core.psi.ext.isAuto
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.psi.ext.withDefaultSubst
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.mergeElementFlags
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region

/**
 * A "trait object" type should not be confused with a trait.
 * Though you use the same path to denote both traits and trait objects,
 * only the latter are ty.
 */
class TyTraitObject(
    val traits: List<BoundElement<RsTraitItem>>,
    val region: Region = ReUnknown,
    override val aliasedBy: BoundElement<RsTypeAlias>? = null
) : Ty(mergeElementFlags(traits) or region.flags) {

    init {
        require(traits.isNotEmpty()) {
            "Can't construct TyTraitObject from empty list of trait bounds"
        }
    }

    val typeArguments: List<Ty>
        get() = traits.flatMap { it.element.typeParameters }.map { typeParameterValues[it] ?: TyUnknown }

    override val typeParameterValues: Substitution
        get() = traits.map { it.subst }.fold(emptySubstitution) { a, b -> a + b }

    override fun superFoldWith(folder: TypeFolder): TyTraitObject =
        TyTraitObject(traits.map { it.foldWith(folder) }, region.foldWith(folder), aliasedBy?.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        traits.any { it.visitWith(visitor) } || region.visitWith(visitor)

    override fun withAlias(aliasedBy: BoundElement<RsTypeAlias>): Ty = TyTraitObject(traits, region, aliasedBy)

    fun getTraitBoundsTransitively(): Collection<BoundElement<RsTraitItem>> =
        traits.flatMap { it.flattenHierarchy }

    val baseTrait: RsTraitItem?
        get() {
            val traits = traits.map { it.element }
            return traits.singleOrNull() ?: traits.singleOrNull { !it.isAuto }
        }

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        javaClass != other?.javaClass -> false
        other !is TyTraitObject -> false
        traits != other.traits -> false
        aliasedBy != other.aliasedBy -> false
        else -> true
    }

    override fun hashCode(): Int = traits.hashCode()

    override fun isEquivalentToInner(other: Ty): Boolean {
        if (this === other) return true
        if (other !is TyTraitObject) return false

        if (traits.size != other.traits.size) return false
        for (i in traits.indices) {
            val be1 = traits[i]
            val be2 = other.traits[i]
            if (!be1.isEquivalentTo(be2)) return false
        }

        return true
    }

    companion object {
        fun valueOf(trait: RsTraitItem): TyTraitObject {
            val item = CompletionUtil.getOriginalOrSelf(trait)
            return TyTraitObject(listOf(item.withDefaultSubst()))
        }
    }
}
