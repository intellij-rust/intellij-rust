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

    /** Returns `Some` if this is a reference to a thread-local static item that is used to access that static. */
    val isRefToThreadLocal: Boolean
        get() = if (localInfo is MirLocalInfo.StaticRef) {
            localInfo.isThreadLocal
        } else {
            false
        }

    /** Returns `true` if this is a reference to a static item that is used to access that static. */
    val isRefToStatic: Boolean
        get() = localInfo is MirLocalInfo.StaticRef

    override fun toString(): String = "_$index: $ty"

    fun copy(mutability: Mutability, source: MirSourceInfo, localInfo: MirLocalInfo): MirLocal {
        return MirLocal(index, mutability, internal, localInfo, blockTail, ty, source)
    }

    companion object {
        val fake = MirLocal(-1, Mutability.MUTABLE, true, null, null, TyUnknown, MirSourceInfo.fake)
    }
}

fun MirLocal.intoPlace(): MirPlace = MirPlace(this)
