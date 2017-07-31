/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.resolve.ImplLookup

data class TyFunction(val paramTypes: List<Ty>, val retType: Ty) : Ty {

    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult {
        return if(other is TyFunction && paramTypes.size == other.paramTypes.size) {
            UnifyResult.mergeAll(
                paramTypes.zip(other.paramTypes).map { (type1, type2) -> type1.unifyWith(type2, lookup) }
            ).merge(retType.unifyWith(other.retType, lookup))
        } else {
            UnifyResult.fail
        }
    }

    override fun toString(): String {
        val params = paramTypes.joinToString(", ", "fn(", ")")
        return if (retType === TyUnit) params else "$params -> $retType"
    }

    override fun substitute(subst: Substitution): TyFunction =
        TyFunction(paramTypes.map { it.substitute(subst) }, retType.substitute(subst))
}
