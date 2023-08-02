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

    operator fun get(index: Int): MirLocal = locals[index]

    /**
     * This function will change the object that is stored at the [index].
     */
    fun update(index: Int, mutability: Mutability, source: MirSourceInfo, localInfo: MirLocalInfo) {
        locals[index] = locals[index].copy(mutability, source, localInfo)
    }

    fun newLocal(
        mutability: Mutability = Mutability.MUTABLE,
        internal: Boolean = false,
        localInfo: MirLocalInfo? = null,
        blockTail: MirBlockTailInfo? = null,
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
