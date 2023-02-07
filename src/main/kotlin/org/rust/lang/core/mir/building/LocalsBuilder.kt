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
    private val returnLocal = MirLocal.returnLocal(returnTy, returnSource)
    private val tail = mutableListOf<MirLocal>()

    fun returnPlace() = MirPlace(returnLocal)

    fun tempPlace(ty: Ty, source: MirSourceInfo, mutability: Mutability): MirPlace {
        val local = MirLocal(mutability, ty, source)
        tail.add(local)
        return MirPlace(local)
    }

    fun build(): List<MirLocal> {
        return buildList {
            add(returnLocal)
            addAll(tail)
        }
    }
}
