/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.resolve.ImplLookup

data class TyTuple(val types: List<Ty>) : Ty {

    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult {
        return if (other is TyTuple && types.size == other.types.size) {
            UnifyResult.mergeAll(types.zip(other.types).map { (type1, type2) -> type1.unifyWith(type2, lookup) })
        } else {
            UnifyResult.fail
        }
    }

    override fun substitute(subst: Substitution): TyTuple =
        TyTuple(types.map { it.substitute(subst) })

    override fun toString(): String = tyToString(this)
}

