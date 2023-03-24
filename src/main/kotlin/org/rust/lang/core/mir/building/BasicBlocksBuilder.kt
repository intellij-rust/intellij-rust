/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.impls.MirBasicBlockImpl

class BasicBlocksBuilder {
    private val startBlock = MirBasicBlockImpl(false)
    private val tail = mutableListOf<MirBasicBlockImpl>()

    fun startBlock(): BlockAnd<Unit> = startBlock and Unit

    fun new(unwind: Boolean = false): MirBasicBlockImpl {
        return MirBasicBlockImpl(unwind).also { tail.add(it) }
    }

    fun build(): MutableList<MirBasicBlockImpl> = mutableListOf<MirBasicBlockImpl>().apply {
        add(startBlock)
        addAll(tail)
    }
}
