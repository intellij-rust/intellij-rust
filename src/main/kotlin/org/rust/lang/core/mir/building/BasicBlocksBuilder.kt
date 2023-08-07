/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.impls.MirBasicBlockImpl

class BasicBlocksBuilder {
    private val basicBlocks = mutableListOf<MirBasicBlockImpl>()

    init {
        new(false)
    }

    fun startBlock(): BlockAnd<Unit> = basicBlocks[0] and Unit

    fun new(unwind: Boolean = false): MirBasicBlockImpl {
        val bb = MirBasicBlockImpl(basicBlocks.size, unwind)
        basicBlocks.add(bb)
        return bb
    }

    fun build(): MutableList<MirBasicBlockImpl> = basicBlocks
}
