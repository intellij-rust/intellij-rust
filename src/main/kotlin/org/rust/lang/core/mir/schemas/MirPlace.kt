/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.types.ty.Ty

typealias PlaceElem = MirProjectionElem<Ty>

data class MirPlace(val local: MirLocal, val projections: List<PlaceElem> = emptyList()) {
    fun makeField(fieldIndex: Int, ty: Ty): MirPlace {
        val newProjections = projections.toMutableList()
        newProjections.add(MirProjectionElem.Field(fieldIndex, ty))
        return MirPlace(local, newProjections)
    }
}
