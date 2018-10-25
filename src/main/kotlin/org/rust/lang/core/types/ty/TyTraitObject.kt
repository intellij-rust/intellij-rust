/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.lifetimeParameters
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.mergeFlags
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region

/**
 * A "trait object" type should not be confused with a trait.
 * Though you use the same path to denote both traits and trait objects,
 * only the latter are ty.
 */
class TyTraitObject(
    val trait: BoundElement<RsTraitItem>,
    val region: Region = ReUnknown
) : Ty(mergeFlags(trait) or region.flags) {

    val typeArguments: List<Ty>
        get() = trait.element.typeParameters.map { typeParameterValues[it] ?: TyUnknown }

    override val typeParameterValues: Substitution
        get() = trait.subst

    override fun superFoldWith(folder: TypeFolder): TyTraitObject =
        TyTraitObject(trait.foldWith(folder), region.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        trait.visitWith(visitor) || region.visitWith(visitor)

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        javaClass != other?.javaClass -> false
        other !is TyTraitObject -> false
        trait != other.trait -> false
        else -> true
    }

    override fun hashCode(): Int = trait.hashCode()

    companion object {
        fun valueOf(trait: RsTraitItem): TyTraitObject {
            val item = CompletionUtil.getOriginalOrSelf(trait)
            return TyTraitObject(item.withDefaultSubst())
        }
    }
}

fun RsTraitItem.withDefaultSubst(): BoundElement<RsTraitItem> =
    BoundElement(this, defaultSubstitution(this))

private fun defaultSubstitution(item: RsTraitItem): Substitution {
    val typeSubst = item.typeParameters.associate {
        val parameter = TyTypeParameter.named(it)
        parameter to parameter
    }
    val regionSubst = item.lifetimeParameters.associate {
        val parameter = ReEarlyBound(it)
        parameter to parameter
    }
    return Substitution(typeSubst, regionSubst)
}
