/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.resolve.ImplLookup

data class TySlice(val elementType: Ty) : Ty {
    override fun canUnifyWith(other: Ty, lookup: ImplLookup, mapping: TypeMapping?): Boolean = merge(mapping) {
        other is TySlice && elementType.canUnifyWith(other.elementType, lookup, it) ||
            other is TyArray && elementType.canUnifyWith(other.base, lookup, it)
    }

    override fun substitute(subst: Substitution): Ty {
        return TySlice(elementType.substitute(subst))
    }

    override fun toString() = "[$elementType]"
}
