/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.resolve.ImplLookup

data class TyPointer(val referenced: Ty, val mutable: Boolean = false) : Ty {

    override fun canUnifyWith(other: Ty, lookup: ImplLookup, mapping: TypeMapping?): Boolean = merge(mapping) {
        other is TyPointer && referenced.canUnifyWith(other.referenced, lookup, it)
    }

    override fun toString() = "*${if (mutable) "mut" else "const"} $referenced"

    override fun substitute(subst: Substitution): Ty =
        TyPointer(referenced.substitute(subst), mutable)
}
