/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

class TyArray(val base: Ty, val size: Int?) : Ty {
    override fun unifyWith(other: Ty, lookup: ImplLookup): UnifyResult =
        if (other is TyArray && (size == null || other.size == null || size == other.size))
            base.unifyWith(other.base, lookup) else UnifyResult.fail

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyArray(base.foldWith(folder), size)

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        base.visitWith(visitor)

    override fun toString(): String = tyToString(this)
}
