/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.MirLocal
import org.rust.lang.core.mir.schemas.MirPlace
import org.rust.lang.core.mir.schemas.PlaceElem

class PlaceBuilder(private val base: PlaceBase, private val projections: List<PlaceElem>) {
    constructor(local: MirLocal) : this(PlaceBase.Local(local), emptyList())

    fun toPlace(): MirPlace {
        when (base) {
            is PlaceBase.Local -> return MirPlace(base.local, projections)
        }
    }
}
