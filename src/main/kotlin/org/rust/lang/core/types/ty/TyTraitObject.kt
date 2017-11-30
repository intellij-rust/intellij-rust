/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.ide.presentation.tyToString
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor


/**
 * A "trait object" type should not be confused with a trait.
 * Though you use the same path to denote both traits and trait objects,
 * only the latter are ty.
 */
data class TyTraitObject(val trait: BoundElement<RsTraitItem>) : Ty(mergeFlags(trait)) {

    val typeArguments: List<Ty>
        get() = trait.element.typeParameters.map { typeParameterValues.get(it) ?: TyUnknown }

    override val typeParameterValues: Substitution
        get() = trait.subst

    override fun superFoldWith(folder: TypeFolder): TyTraitObject =
        TyTraitObject(trait.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        trait.visitWith(visitor)

    override fun toString(): String = tyToString(this)

    companion object {
        fun valueOf(trait: RsTraitItem): TyTraitObject {
            val item = CompletionUtil.getOriginalOrSelf(trait)
            return TyTraitObject(BoundElement(item, defaultSubstitution(item)))
        }
    }
}

private fun defaultSubstitution(item: RsTraitItem): Substitution =
    item.typeParameters.associate { rsTypeParameter ->
        val tyTypeParameter = TyTypeParameter.named(rsTypeParameter)
        tyTypeParameter to tyTypeParameter
    }
