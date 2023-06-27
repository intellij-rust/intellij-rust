/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.MirLocal
import org.rust.lang.core.mir.schemas.MirPlace
import org.rust.lang.core.mir.schemas.MirProjectionElem
import org.rust.lang.core.mir.schemas.PlaceElem
import org.rust.lang.core.psi.RsEnumItem
import org.rust.lang.core.thir.MirFieldIndex
import org.rust.lang.core.thir.MirVariantIndex
import org.rust.lang.core.thir.variant
import org.rust.lang.core.types.ty.Ty

data class PlaceBuilder(private val base: PlaceBase, private val projections: MutableList<PlaceElem>) {
    constructor(local: MirLocal) : this(PlaceBase.Local(local), mutableListOf())

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/expr/as_place.rs#L255
    fun toPlace(): MirPlace = tryToPlace()!!

    fun tryToPlace(): MirPlace? {
        when (base) {
            is PlaceBase.Local -> return MirPlace(base.local, projections.toList())
        }
    }

    fun field(fieldIndex: MirFieldIndex, ty: Ty): PlaceBuilder =
        project(MirProjectionElem.Field(fieldIndex, ty))

    fun index(index: MirLocal): PlaceBuilder = project(MirProjectionElem.Index(index))

    fun project(element: PlaceElem): PlaceBuilder {
        projections.add(element)
        return this
    }

    fun cloneProject(element: PlaceElem): PlaceBuilder = clone().project(element)

    fun deref(): PlaceBuilder = apply {
        projections.add(MirProjectionElem.Deref)
    }

    fun clone(): PlaceBuilder = PlaceBuilder(base, projections.toMutableList())

    fun downcast(item: RsEnumItem, variantIndex: MirVariantIndex): PlaceBuilder =
        project(MirProjectionElem.Downcast(item.variant(variantIndex).name, variantIndex))
}
