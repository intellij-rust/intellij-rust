/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir

import org.rust.lang.core.mir.schemas.MirSourceInfo
import org.rust.lang.core.psi.ext.ArithmeticOp
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.ty.*

val Ty.isSigned: Boolean
    get() = this is TyInteger.I8
        || this is TyInteger.I16
        || this is TyInteger.I32
        || this is TyInteger.I64
        || this is TyInteger.I128
        || this is TyInteger.ISize

val TyInteger.minValue: Long
    get() {
        assert(isSigned)
        // TODO
        return Int.MIN_VALUE.toLong()
    }

val RsElement.asSource: MirSourceInfo get() = MirSourceInfo(this)

val Ty.needsDrop: Boolean
    get() {
        // TODO: it's just a dummy impl
        return when (this) {
            is TyPrimitive -> false
            is TyTuple -> types.any { it.needsDrop }
            else -> true
        }
    }

val ArithmeticOp.isCheckable: Boolean
    get() = this == ArithmeticOp.ADD
        || this == ArithmeticOp.SUB
        || this == ArithmeticOp.MUL
        || this == ArithmeticOp.SHL
        || this == ArithmeticOp.SHR
