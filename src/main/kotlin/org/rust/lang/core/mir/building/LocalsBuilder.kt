/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.MirLocal
import org.rust.lang.core.mir.schemas.MirPlace
import org.rust.lang.core.mir.schemas.MirSourceInfo
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.Ty

class LocalsBuilder(returnTy: Ty, returnSource: MirSourceInfo) {
    private val locals = mutableListOf(MirLocal.returnLocal(returnTy, returnSource))

    fun returnPlace() = MirPlace(locals.first())

    fun tempPlace(
        ty: Ty,
        source: MirSourceInfo,
        internal: Boolean = false,
        mutability: Mutability = Mutability.MUTABLE,
    ): MirPlace {
        val local = MirLocal(mutability, internal, null, null, ty, source)
        locals.add(local)
        return MirPlace(local)
    }

    fun push(local: MirLocal) {
        locals.add(local)
    }

    fun build(): List<MirLocal> = locals.toList()
}
