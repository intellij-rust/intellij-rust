/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.types.ty.Ty

sealed class MirConstant(
    val source: MirSourceInfo,
) {
    class Value(val constValue: MirConstValue, val ty: Ty, source: MirSourceInfo) : MirConstant(source) {
        override fun toString() = "Value(constValue=$constValue, ty=$ty)"
    }
}
