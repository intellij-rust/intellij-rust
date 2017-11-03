/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.ide.presentation.tyToString
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

class TyArray(val base: Ty, val size: Long?) : Ty(base.flags) {
    override fun superFoldWith(folder: TypeFolder): Ty =
        TyArray(base.foldWith(folder), size)

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        base.visitWith(visitor)

    override fun toString(): String = tyToString(this)
}
