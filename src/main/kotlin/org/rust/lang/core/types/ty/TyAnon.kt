/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTraitType
import org.rust.lang.core.psi.ext.flattenHierarchy
import org.rust.lang.core.psi.ext.isImpl
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.mergeElementFlags

/**
 * Represents "impl Trait".
 */
data class TyAnon(
    val definition: RsTraitType?,
    val traits: List<BoundElement<RsTraitItem>>
) : Ty(mergeElementFlags(traits)) {

    init {
        require(definition == null || definition.isImpl) {
            "Can't construct TyAnon from non `impl Trait` definition $definition"
        }
    }

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyAnon(definition, traits.map { it.foldWith(folder) })

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        traits.any { it.visitWith(visitor) }

    fun getTraitBoundsTransitively(): Collection<BoundElement<RsTraitItem>> =
        traits.flatMap { it.flattenHierarchy }
}
