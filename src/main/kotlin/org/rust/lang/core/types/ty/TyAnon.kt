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

/**
 * Represents "impl Trait".
 */
data class TyAnon(val definition: RsTraitType, val traits: List<BoundElement<RsTraitItem>>) : Ty() {

    init {
        check(definition.isImpl) { "Can't construct TyAnon from non `impl Trait` definition $definition" }
    }

    fun getTraitBoundsTransitively(): Collection<BoundElement<RsTraitItem>> =
        traits.flatMap { it.flattenHierarchy }
}
