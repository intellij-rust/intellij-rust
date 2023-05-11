/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.mir.WithIndex
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown

class MirLocal(
    override val index: Int,
    val mutability: Mutability,
    val internal: Boolean,
    val localInfo: MirLocalInfo?,
    val blockTail: MirBlockTailInfo?,
    val ty: Ty,
    val source: MirSourceInfo,
) : WithIndex {
    override fun toString(): String = "_$index: $ty"

    companion object {
        val fake = MirLocal(-1, Mutability.MUTABLE, true, null, null, TyUnknown, MirSourceInfo.fake)
    }
}
