/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

// TODO: int -> uint128, size -> non zero u8
data class MirScalarInt(
    val data: Long,
    val size: Byte,
) {
    // TODO there are some checks, maybe it'll be needed later
    fun toBits() = data
}
