/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.infer.TypeFolder

data class TySlice(val elementType: Ty) : Ty {
    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult {
        return when (other) {
            is TySlice -> elementType.unifyWith(other.elementType, lookup)
            is TyArray -> elementType.unifyWith(other.base, lookup)
            else -> UnifyResult.fail
        }
    }

    override fun superFoldWith(folder: TypeFolder): Ty =
        TySlice(elementType.foldWith(folder))

    override fun toString(): String = tyToString(this)
}
