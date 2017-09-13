/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.infer.TypeFoldable
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.ty.get

/**
 * A complete reference to a trait. These take numerous guises in syntax,
 * but perhaps the most recognizable form is in a where clause:
 *     `T : Foo<U>`
 */
data class TraitRef(val selfTy: Ty, val trait: BoundElement<RsTraitItem>): TypeFoldable<TraitRef> {
    override fun superFoldWith(folder: TypeFolder): TraitRef =
        TraitRef(selfTy.foldWith(folder), trait.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        selfTy.visitWith(visitor) || trait.visitWith(visitor)

    override fun toString(): String {
        val (item, subst) = trait
        val tyArgs = item.typeParameters.map { subst.get(it) ?: TyUnknown }
        return "$selfTy: ${trait.element.name}" + (if (tyArgs.isEmpty()) "" else tyArgs.joinToString(", ", "<", ">"))
    }
}
