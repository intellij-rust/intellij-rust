/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.impls.MirBasicBlockImpl

data class BlockAnd<T>(val block: MirBasicBlockImpl, val elem: T)

infix fun <T> MirBasicBlockImpl.and(elem: T) = BlockAnd(this, elem)

fun MirBasicBlockImpl.andUnit() = BlockAnd(this, Unit)

inline fun <T, R> BlockAnd<T>.map(transform: (T) -> R): BlockAnd<R> {
    return block and transform(elem)
}

