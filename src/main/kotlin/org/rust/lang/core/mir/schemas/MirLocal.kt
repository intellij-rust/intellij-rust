/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown

class MirLocal(
    val mutability: Mutability,
    val internal: Boolean,
    val localInfo: MirLocalInfo?,
    val blockTail: MirBlockTailInfo?,
    val ty: Ty,
    val source: MirSourceInfo,
) {
    companion object {
        fun returnLocal(ty: Ty, source: MirSourceInfo) = MirLocal(
            mutability = Mutability.MUTABLE,
            internal = false,
            localInfo = null,
            blockTail = null,
            ty = ty,
            source = source,
        )
        val fake = MirLocal(Mutability.MUTABLE, true, null, null, TyUnknown, MirSourceInfo.fake)
    }
}
