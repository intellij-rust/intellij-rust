/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.resolve.ImplLookup


/**
 * A "trait object" type should not be confused with a trait.
 * Though you use the same path to denote both traits and trait objects,
 * only the latter are ty.
 */
data class TyTraitObject(val trait: RsTraitItem) : Ty {

    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult =
        UnifyResult.exactIf(other is RsTraitItem && trait == other.trait)

    override fun toString(): String = trait.name ?: "<anonymous>"
}
