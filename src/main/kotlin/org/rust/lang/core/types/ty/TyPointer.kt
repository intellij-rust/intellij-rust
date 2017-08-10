/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.resolve.ImplLookup

data class TyPointer(val referenced: Ty, val mutable: Boolean = false) : Ty {

    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult =
        if (other is TyPointer) referenced.unifyWith(other.referenced, lookup) else UnifyResult.fail

    override fun substitute(subst: Substitution): Ty =
        TyPointer(referenced.substitute(subst), mutable)

    override fun toString() = "*${if (mutable) "mut" else "const"} $referenced"
}
