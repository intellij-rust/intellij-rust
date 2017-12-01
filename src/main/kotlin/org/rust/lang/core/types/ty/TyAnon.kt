/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.flattenHierarchy
import org.rust.lang.core.types.BoundElement

/**
 * Represents "impl Trait".
 */
data class TyAnon(val traits: List<BoundElement<RsTraitItem>>) : Ty() {

    fun getTraitBoundsTransitively(): Collection<BoundElement<RsTraitItem>> =
        traits.flatMap { it.flattenHierarchy }

    override fun toString(): String = tyToString(this)
}
