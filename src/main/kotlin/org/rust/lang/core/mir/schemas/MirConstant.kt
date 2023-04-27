/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.types.ty.Ty

sealed class MirConstant(
    val span: MirSpan,
) {
    class Value(val constValue: MirConstValue, val ty: Ty, span: MirSpan) : MirConstant(span) {
        override fun toString() = "Value(constValue=$constValue, ty=$ty)"
    }

    companion object {
        fun zeroSized(ty: Ty, span: MirSpan): MirConstant = Value(MirConstValue.ZeroSized, ty, span)
    }
}
