/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.mir.isSigned
import org.rust.lang.core.types.ty.Mutability
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyInteger

sealed class MirCastTy {
    data class Int(val ty: MirIntTy) : MirCastTy()
    object Float : MirCastTy()
    object FnPtr : MirCastTy()
    data class Pointer(val ty: Ty, val mutability: Mutability) : MirCastTy()
    object DynStar : MirCastTy()

    companion object {
        fun from(ty: Ty): MirCastTy? = when {
            ty is TyInteger && ty.isSigned -> Int(MirIntTy.I)
            else -> TODO()
        }
    }
}

sealed class MirIntTy {
    data class U(val ty: MirUintTy) : MirIntTy()
    object I : MirIntTy()
    object CEnum : MirIntTy()
    object Bool : MirIntTy()
    object Char : MirIntTy()
}

// This is the only class that is in the type system module in compiler, but we don't have TyUInteger or smth
enum class MirUintTy {
    Usize,
    U8,
    U16,
    U32,
    U64,
    U128,
}
