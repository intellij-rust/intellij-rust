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
    val ty: Ty,
    val source: MirSourceInfo,
) {
    override fun toString(): String {
        return "MirLocal(mutability=$mutability, ty=$ty, source=$source)"
    }

    companion object {
        fun returnLocal(ty: Ty, source: MirSourceInfo) = MirLocal(Mutability.MUTABLE, ty, source)
        val fake = MirLocal(Mutability.MUTABLE, TyUnknown, MirSourceInfo.Fake)
    }
}
