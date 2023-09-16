/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

sealed class MirOperand {
    abstract fun toCopy(): MirOperand

    data class Constant(val constant: MirConstant) : MirOperand() {
        override fun toCopy(): MirOperand = this
    }

    data class Move(val place: MirPlace) : MirOperand() {
        override fun toCopy(): MirOperand = Copy(place)
    }

    data class Copy(val place: MirPlace) : MirOperand() {
        override fun toCopy(): MirOperand = this
    }
}
