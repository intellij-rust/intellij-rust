/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.impls.MirBasicBlockImpl

interface DropTreeBuilder {
    fun makeBlock(): MirBasicBlockImpl
    fun addEntry(from: MirBasicBlockImpl, to: MirBasicBlockImpl)
}

class ExitScopes(private val basicBlocks: BasicBlocksBuilder) : DropTreeBuilder {
    override fun makeBlock() = basicBlocks.new()
    override fun addEntry(from: MirBasicBlockImpl, to: MirBasicBlockImpl) = from.terminateWithGoto(to, null)
}

class Unwind(private val basicBlocks: BasicBlocksBuilder) : DropTreeBuilder {
    override fun makeBlock() = basicBlocks.new(unwind = true)
    override fun addEntry(from: MirBasicBlockImpl, to: MirBasicBlockImpl) {
        from.unwindTerminatorTo(to)
    }
}
