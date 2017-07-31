/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.resolve.ImplLookup

data class TySlice(val elementType: Ty) : Ty {
    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult {
        return when (other) {
            is TySlice -> elementType.unifyWith(other.elementType, lookup)
            is TyArray -> elementType.unifyWith(other.base, lookup)
            else -> UnifyResult.fail
        }
    }

    override fun substitute(subst: Substitution): Ty {
        return TySlice(elementType.substitute(subst))
    }

    override fun toString() = "[$elementType]"
}
