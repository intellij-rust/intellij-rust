/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.resolve.ImplLookup

class TyArray(val base: Ty, val size: Int) : Ty {
    override fun canUnifyWith(other: Ty, lookup: ImplLookup, mapping: TypeMapping?): Boolean = merge(mapping) {
        other is TyArray && size == other.size && base.canUnifyWith(other.base, lookup, it)
    }

    override fun substitute(subst: Substitution): Ty =
        TyArray(base.substitute(subst), size)

    override fun toString() = "[$base; $size]"
}
