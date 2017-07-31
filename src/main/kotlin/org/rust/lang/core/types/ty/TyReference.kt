/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.resolve.ImplLookup

data class TyReference(val referenced: Ty, val mutable: Boolean = false) : Ty {

    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult =
        if (other is TyReference) referenced.unifyWith(other.referenced, lookup) else UnifyResult.fail

    override fun toString(): String = "${if (mutable) "&mut " else "&"}$referenced"

    override fun substitute(subst: Substitution): Ty =
        TyReference(referenced.substitute(subst), mutable)
}
