/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.impls

import org.rust.lang.core.mir.dataflow.framework.Direction
import org.rust.lang.core.mir.dataflow.framework.Forward
import org.rust.lang.core.mir.dataflow.framework.GenKillAnalysis
import org.rust.lang.core.mir.dataflow.move.DropFlagState
import org.rust.lang.core.mir.dataflow.move.MoveData
import org.rust.lang.core.mir.dataflow.move.MovePath
import org.rust.lang.core.mir.dataflow.move.dropFlagEffectsForLocation
import org.rust.lang.core.mir.schemas.*
import java.util.*

class MaybeUninitializedPlaces(private val moveData: MoveData) : GenKillAnalysis {

    override val direction: Direction = Forward

    // bottom = all initialized
    override fun bottomValue(body: MirBody): BitSet = BitSet(moveData.movePathsCount)

    // set all bits to 1 (uninit) before gathering counter-evidence
    override fun initializeStartBlock(body: MirBody, state: BitSet) {
        state.set(0, moveData.movePathsCount)
    }

    override fun join(state1: BitSet, state2: BitSet): Boolean {
        // TODO optimize, do not copy
        val old = state1.clone()
        state1.or(state2)
        return state1 != old
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

    private fun updateBits(state: BitSet, movePath: MovePath, movePathState: DropFlagState) {
        state[movePath.index] = movePathState == DropFlagState.Absent
    }
}
