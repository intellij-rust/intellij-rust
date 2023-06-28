/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.builtinDeref
import org.rust.lang.core.types.ty.builtinIndex

typealias PlaceElem = MirProjectionElem<Ty>

data class MirPlace(val local: MirLocal, val projections: List<PlaceElem> = emptyList()) {
    fun makeField(fieldIndex: Int, ty: Ty): MirPlace {
        val newProjections = projections.toMutableList()
        newProjections.add(MirProjectionElem.Field(fieldIndex, ty))
        return MirPlace(local, newProjections)
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_middle/src/mir/tcx.rs#L135
    fun ty(): MirPlaceTy = tyFrom(local, projections)

    companion object {
        // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_middle/src/mir/tcx.rs#L119
        fun tyFrom(local: MirLocal, projections: List<MirProjectionElem<Ty>>): MirPlaceTy {
            return projections.fold(MirPlaceTy.fromTy(local.ty)) { placeTy, element ->
                placeTy.projectionTy(element)
            }
        }
    }
}

class MirPlaceTy(
    val ty: Ty,
    /** Downcast to a particular variant of an enum or a generator, if included */
    val variantIndex: Int?,
) {

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_middle/src/mir/tcx.rs#L57
    fun projectionTy(element: MirProjectionElem<Ty>): MirPlaceTy {
        check(variantIndex == null || element is MirProjectionElem.Field) {
            "cannot use non field projection on downcasted place"
        }
        return when (element) {
            is MirProjectionElem.Field -> fromTy(element.elem)
            is MirProjectionElem.Deref -> {
                val (ty, _) = ty.builtinDeref(items = null)
                    ?: error("deref projection of non-dereferenceable ty")
                fromTy(ty)
            }
            is MirProjectionElem.Index,
            is MirProjectionElem.ConstantIndex -> fromTy(ty.builtinIndex()!!)
            is MirProjectionElem.Downcast -> MirPlaceTy(ty, element.variantIndex)
        }
    }

    companion object {
        fun fromTy(ty: Ty): MirPlaceTy = MirPlaceTy(ty, variantIndex = null)
    }
}
