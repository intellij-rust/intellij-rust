/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.psi.ext.ArithmeticOp

sealed class MirAssertKind {
    data class BoundsCheck(val len: MirOperand, val index: MirOperand) : MirAssertKind()
    data class OverflowNeg(val arg: MirOperand) : MirAssertKind()
    data class Overflow(val op: ArithmeticOp, val left: MirOperand, val right: MirOperand) : MirAssertKind()
    data class DivisionByZero(val arg: MirOperand) : MirAssertKind()
    data class ReminderByZero(val arg: MirOperand) : MirAssertKind()
}
