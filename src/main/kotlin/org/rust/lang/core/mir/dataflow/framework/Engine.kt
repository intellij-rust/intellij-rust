/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.framework

import org.rust.lang.core.mir.schemas.MirBasicBlock
import org.rust.lang.core.mir.schemas.MirBody

/** A solver for dataflow problems. */
class Engine<Domain>(
    private val body: MirBody,
    private val analysis: Analysis<Domain>,
) {

    private val blockStates: List<Domain> = List(body.basicBlocks.size) { analysis.bottomValue(body) }

    init {
        analysis.initializeStartBlock(body, blockStates.first())
    }

    fun iterateToFixPoint(): Results<Domain> {
        val dirtyQueue = WorkQueue<MirBasicBlock>(body.basicBlocks.size)
        for (block in orderBasicBlocks()) {
            dirtyQueue.insert(block)
        }

        while (!dirtyQueue.isEmpty()) {
            val block = dirtyQueue.pop()
            val initialBlockState = blockStates[block.index]
            val blockState = analysis.copyState(initialBlockState)
            analysis.direction.applyEffectsInBlock(analysis, blockState, block)
            analysis.direction.joinStateIntoSuccessorsOf(analysis, blockState, block) { target, targetState ->
                val changed = analysis.join(blockStates[target.index], targetState)
                if (changed) {
                    dirtyQueue.insert(target)
                }
            }
        }
        return Results(analysis, blockStates)
    }

    /** Reverse post order for [Forward] direction */
    private fun orderBasicBlocks(): List<MirBasicBlock> {
        val result = body.getBasicBlocksInPostOrder()
        return when (analysis.direction) {
            is Forward -> result.asReversed()
            // is Backward -> result
        }
    }
}
