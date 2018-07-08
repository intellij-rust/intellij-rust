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

/**
 * A "trait object" type should not be confused with a trait.
 * Though you use the same path to denote both traits and trait objects,
 * only the latter are ty.
 */
data class TyTraitObject(val trait: BoundElement<RsTraitItem>) : Ty(mergeFlags(trait)) {

    val typeArguments: List<Ty>
        get() = trait.element.typeParameters.map { typeParameterValues[it] ?: TyUnknown }

    override val typeParameterValues: Substitution
        get() = trait.subst

    override fun superFoldWith(folder: TypeFolder): TyTraitObject =
        TyTraitObject(trait.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        trait.visitWith(visitor)

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
    val lifetimeSubst = item.lifetimeParameters.associate {
        val parameter = ReEarlyBound(it)
        parameter to parameter
    }
    return Substitution(typeSubst, lifetimeSubst)
}
