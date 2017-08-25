/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.infer.TypeFolder

data class TyReference(val referenced: Ty, val mutability: Mutability) : Ty {

    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult =
        if (other is TyReference) referenced.unifyWith(other.referenced, lookup) else UnifyResult.fail

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyReference(referenced.foldWith(folder), mutability)

    override fun toString(): String = tyToString(this)
}
