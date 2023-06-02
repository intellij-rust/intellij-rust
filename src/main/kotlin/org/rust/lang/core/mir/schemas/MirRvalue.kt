/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.UnaryOperator
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.ty.Ty

sealed class MirRvalue {
    data class Use(val operand: MirOperand) : MirRvalue()
    data class UnaryOpUse(val op: UnaryOperator, val operand: MirOperand) : MirRvalue()
    data class BinaryOpUse(val op: MirBinaryOperator, val left: MirOperand, val right: MirOperand) : MirRvalue()
    data class CheckedBinaryOpUse(val op: MirBinaryOperator, val left: MirOperand, val right: MirOperand) : MirRvalue()
    sealed class Aggregate(val operands: List<MirOperand>) : MirRvalue() {
        class Array(val ty: Ty, operands: List<MirOperand>) : Aggregate(operands)
        class Tuple(operands: List<MirOperand>) : Aggregate(operands)
        class Adt(val definition: RsStructItem, operands: List<MirOperand>) : Aggregate(operands)
    }
    data class Repeat(val operand: MirOperand, val count: Const) : MirRvalue()
    data class Ref(val borrowKind: MirBorrowKind, val place: MirPlace) : MirRvalue()
    data class Len(val place: MirPlace) : MirRvalue()
}
