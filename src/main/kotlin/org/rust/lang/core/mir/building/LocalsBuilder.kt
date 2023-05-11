/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.Ty

class LocalsBuilder(returnTy: Ty, returnSource: MirSourceInfo) {
    private val locals: MutableList<MirLocal> = mutableListOf()

    init {
        allocateReturnLocal(returnTy, returnSource)
    }

    fun returnPlace() = MirPlace(locals.first())

    fun newTempPlace(
        ty: Ty,
        source: MirSourceInfo,
        internal: Boolean = false,
        mutability: Mutability = Mutability.MUTABLE,
    ): MirPlace {
        val local = newLocal(mutability, internal, null, null, ty, source)
        return MirPlace(local)
    }

    fun newLocal(
        mutability: Mutability,
        internal: Boolean,
        localInfo: MirLocalInfo?,
        blockTail: MirBlockTailInfo?,
        ty: Ty,
        source: MirSourceInfo,
    ): MirLocal {
        val local = MirLocal(
            locals.size,
            mutability,
            internal,
            localInfo,
            blockTail,
            ty,
            source,
        )
        locals.add(local)
        return local
    }

    fun build(): List<MirLocal> = locals.toList()

    private fun allocateReturnLocal(ty: Ty, source: MirSourceInfo) = newLocal(
        mutability = Mutability.MUTABLE,
        internal = false,
        localInfo = null,
        blockTail = null,
        ty = ty,
        source = source,
    )
}
