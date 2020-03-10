/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.asLong
import org.rust.lang.core.types.infer.TypeFolder
import org.rust.lang.core.types.infer.TypeVisitor

data class TyArray(val base: Ty, val const: Const) : Ty(base.flags or const.flags) {
    val size: Long? get() = const.asLong()

    override fun superFoldWith(folder: TypeFolder): Ty =
        TyArray(base.foldWith(folder), const.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean =
        base.visitWith(visitor) || const.visitWith(visitor)
}
