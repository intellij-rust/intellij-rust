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

    fun ty(): MirPlaceTy = tyFrom(local, projections)

    companion object {
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

    fun projectionTy(element: MirProjectionElem<Ty>): MirPlaceTy {
        check(variantIndex == null || element !is MirProjectionElem.Field) {
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
        }
    }

    companion object {
        fun fromTy(ty: Ty): MirPlaceTy = MirPlaceTy(ty, variantIndex = null)
    }
}
