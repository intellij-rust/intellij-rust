/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.framework

import org.rust.lang.core.mir.schemas.*
import java.util.*

interface ResultsVisitor<FlowState> {
    fun visitBlockStart(state: FlowState, block: MirBasicBlock) {}
    fun visitBlockEnd(state: FlowState, block: MirBasicBlock) {}

    fun visitStatementBeforePrimaryEffect(state: FlowState, statement: MirStatement, location: MirLocation) {}
    fun visitStatementAfterPrimaryEffect(state: FlowState, statement: MirStatement, location: MirLocation) {}

    fun visitTerminatorBeforePrimaryEffect(state: FlowState, terminator: MirTerminator<MirBasicBlock>, location: MirLocation) {}
    fun visitTerminatorAfterPrimaryEffect(state: FlowState, terminator: MirTerminator<MirBasicBlock>, location: MirLocation) {}
}

/**
 * Things that can be visited by a [ResultsVisitor].
 * It exists so that we can visit the results of multiple dataflow analyses simultaneously.
 */
interface ResultsVisitable<FlowState> {
    val direction: Direction
    fun getCopyOfBlockState(block: MirBasicBlock): FlowState
    fun reconstructBeforeStatementEffect(state: FlowState, statement: MirStatement, location: MirLocation)
    fun reconstructStatementEffect(state: FlowState, statement: MirStatement, location: MirLocation)
    fun reconstructBeforeTerminatorEffect(state: FlowState, terminator: MirTerminator<MirBasicBlock>, location: MirLocation)
    fun reconstructTerminatorEffect(state: FlowState, terminator: MirTerminator<MirBasicBlock>, location: MirLocation)
}

/** A dataflow analysis that has converged to fixpoint. */
class Results<Domain>(
    val analysis: Analysis<Domain>,
    val blockStates: List<Domain>,
) : ResultsVisitable<Domain> {
    override val direction: Direction get() = analysis.direction

    override fun getCopyOfBlockState(block: MirBasicBlock): Domain =
        analysis.copyState(blockStates[block.index])

    override fun reconstructBeforeStatementEffect(state: Domain, statement: MirStatement, location: MirLocation) {
        analysis.applyBeforeStatementEffect(state, statement, location)
    }

    override fun reconstructStatementEffect(state: Domain, statement: MirStatement, location: MirLocation) {
        analysis.applyStatementEffect(state, statement, location)
    }

    override fun reconstructBeforeTerminatorEffect(state: Domain, terminator: MirTerminator<MirBasicBlock>, location: MirLocation) {
        analysis.applyBeforeTerminatorEffect(state, terminator, location)
    }

    override fun reconstructTerminatorEffect(state: Domain, terminator: MirTerminator<MirBasicBlock>, location: MirLocation) {
        analysis.applyTerminatorEffect(state, terminator, location)
    }
}

class BorrowCheckResults(
    private val uninits: Results<BitSet>,
    private val borrows: Results<BitSet>,
    // TODO: EverInitializedPlaces
) : ResultsVisitable<BorrowCheckResults.State> {

    override val direction: Direction get() = Forward

    override fun getCopyOfBlockState(block: MirBasicBlock): State =
        State(uninits.getCopyOfBlockState(block), borrows.getCopyOfBlockState(block))

    override fun reconstructBeforeStatementEffect(state: State, statement: MirStatement, location: MirLocation) {
        uninits.reconstructBeforeStatementEffect(state.uninits, statement, location)
        borrows.reconstructBeforeStatementEffect(state.borrows, statement, location)
    }

    override fun reconstructStatementEffect(state: State, statement: MirStatement, location: MirLocation) {
        uninits.reconstructStatementEffect(state.uninits, statement, location)
        borrows.reconstructStatementEffect(state.borrows, statement, location)
    }

    override fun reconstructBeforeTerminatorEffect(state: State, terminator: MirTerminator<MirBasicBlock>, location: MirLocation) {
        uninits.reconstructBeforeTerminatorEffect(state.uninits, terminator, location)
        borrows.reconstructBeforeTerminatorEffect(state.borrows, terminator, location)
    }

    override fun reconstructTerminatorEffect(state: State, terminator: MirTerminator<MirBasicBlock>, location: MirLocation) {
        uninits.reconstructTerminatorEffect(state.uninits, terminator, location)
        borrows.reconstructTerminatorEffect(state.borrows, terminator, location)
    }

    class State(
        val uninits: BitSet,
        val borrows: BitSet
    )
}

/** Calls the corresponding method in [ResultsVisitor] for every location in a [MirBody] with the dataflow state at that location. */
fun visitResults(
    results: BorrowCheckResults,
    blocks: List<MirBasicBlock>,
    visitor: ResultsVisitor<BorrowCheckResults.State>
) {
    for (block in blocks) {
        results.direction.visitResultsInBlock(block, results, visitor)
    }
}
