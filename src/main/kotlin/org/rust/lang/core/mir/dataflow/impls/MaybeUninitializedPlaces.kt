/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.impls

import org.rust.lang.core.mir.dataflow.framework.Direction
import org.rust.lang.core.mir.dataflow.framework.Forward
import org.rust.lang.core.mir.dataflow.framework.GenKillAnalysis
import org.rust.lang.core.mir.dataflow.move.*
import org.rust.lang.core.mir.schemas.*
import org.rust.openapiext.testAssert
import java.util.*

class MaybeUninitializedPlaces(private val moveData: MoveData) : GenKillAnalysis {

    override val direction: Direction = Forward

    // bottom = all initialized
    override fun bottomValue(body: MirBody): BitSet = BitSet(moveData.movePathsCount)

    // set all bits to 1 (uninit) before gathering counter-evidence
    override fun initializeStartBlock(body: MirBody, state: BitSet) {
        state.set(0, moveData.movePathsCount)
        dropFlagEffectsForFunctionEntry(body, moveData) { path, dropFlagState ->
            testAssert { dropFlagState == DropFlagState.Present }
            state.clear(path.index)
        }
    }

    override fun applyStatementEffect(state: BitSet, statement: MirStatement, location: MirLocation) {
        dropFlagEffectsForLocation(moveData, location) { movePath, movePathState ->
            updateBits(state, movePath, movePathState)
        }
    }

    override fun applyTerminatorEffect(state: BitSet, terminator: MirTerminator<MirBasicBlock>, location: MirLocation) {
        dropFlagEffectsForLocation(moveData, location) { movePath, movePathState ->
            updateBits(state, movePath, movePathState)
        }
    }

    override fun applyCallReturnEffect(state: BitSet, block: MirBasicBlock, returnPlace: MirPlace) {
        val lookupResult = moveData.revLookup.find(returnPlace)
        if (lookupResult is LookupResult.Exact) {
            onAllChildrenBits(lookupResult.movePath) {
                updateBits(state, it, DropFlagState.Present)
            }
        }
    }

    private fun updateBits(state: BitSet, movePath: MovePath, movePathState: DropFlagState) {
        state[movePath.index] = movePathState == DropFlagState.Absent
    }
}
