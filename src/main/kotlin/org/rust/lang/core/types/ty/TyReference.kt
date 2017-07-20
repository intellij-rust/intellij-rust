/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.resolve.ImplLookup

data class TyReference(val referenced: Ty, val mutable: Boolean = false) : Ty {

    override fun canUnifyWith(other: Ty, lookup: ImplLookup, mapping: TypeMapping?): Boolean = merge(mapping) {
        other is TyReference && referenced.canUnifyWith(other.referenced, lookup, it)
    }

    override fun toString(): String = "${if (mutable) "&mut " else "&"}$referenced"

    override fun substitute(subst: Substitution): Ty =
        TyReference(referenced.substitute(subst), mutable)
}
