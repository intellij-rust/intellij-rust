/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.framework

import org.rust.lang.core.mir.schemas.MirBasicBlock
import org.rust.lang.core.mir.schemas.MirLocation
import org.rust.lang.core.mir.schemas.MirTerminator

sealed interface Direction {
    fun <Domain> applyEffectsInBlock(analysis: Analysis<Domain>, state: Domain, block: MirBasicBlock)

    fun <Domain> joinStateIntoSuccessorsOf(
        analysis: Analysis<Domain>,
        exitState: Domain,
        block: MirBasicBlock,
        propagate: (MirBasicBlock, Domain) -> Unit
    )

    fun <FlowState> visitResultsInBlock(
        block: MirBasicBlock,
        results: ResultsVisitable<FlowState>,
        visitor: ResultsVisitor<FlowState>
    )
}

object Forward : Direction {
    override fun <Domain> applyEffectsInBlock(analysis: Analysis<Domain>, state: Domain, block: MirBasicBlock) {
        for ((index, statement) in block.statements.withIndex()) {
            val location = MirLocation(block, index)
            analysis.applyStatementEffect(state, statement, location)
        }

        val terminatorLocation = MirLocation(block, block.statements.size)
        analysis.applyTerminatorEffect(state, block.terminator, terminatorLocation)
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_dataflow/src/framework/direction.rs#L465
    override fun <Domain> joinStateIntoSuccessorsOf(
        analysis: Analysis<Domain>,
        exitState: Domain,
        block: MirBasicBlock,
        propagate: (MirBasicBlock, Domain) -> Unit
    ) {
        when (val terminator = block.terminator) {
            is MirTerminator.Return, is MirTerminator.Resume, is MirTerminator.Unreachable -> Unit
            is MirTerminator.Goto -> propagate(terminator.target, exitState)
            is MirTerminator.Assert -> {
                terminator.unwind?.let { propagate(it, exitState) }
                propagate(terminator.target, exitState)
            }
            is MirTerminator.Drop -> {
                terminator.unwind?.let { propagate(it, exitState) }
                propagate(terminator.target, exitState)
            }
            is MirTerminator.FalseUnwind -> {
                terminator.unwind?.let { propagate(it, exitState) }
                propagate(terminator.realTarget, exitState)
            }
            is MirTerminator.FalseEdge -> {
                propagate(terminator.realTarget, exitState)
                terminator.imaginaryTarget?.let { propagate(it, exitState) }
            }
            is MirTerminator.SwitchInt -> {
                for (target in terminator.targets.targets) {
                    propagate(target, exitState)
                }
            }

            is MirTerminator.Call -> {
                terminator.unwind?.let { propagate(it, exitState) }
                if (terminator.target != null) {
                    analysis.applyCallReturnEffect(exitState, block, terminator.destination)
                    propagate(terminator.target, exitState)
                }
            }
        }
    }

    override fun <FlowState> visitResultsInBlock(
        block: MirBasicBlock,
        results: ResultsVisitable<FlowState>,
        visitor: ResultsVisitor<FlowState>
    ) {
        val state = results.getCopyOfBlockState(block)
        visitor.visitBlockStart(state, block)
        for ((index, statement) in block.statements.withIndex()) {
            val location = MirLocation(block, index)
            results.reconstructBeforeStatementEffect(state, statement, location)
            visitor.visitStatementBeforePrimaryEffect(state, statement, location)
            results.reconstructStatementEffect(state, statement, location)
            visitor.visitStatementAfterPrimaryEffect(state, statement, location)
        }

        val terminatorLocation = MirLocation(block, block.statements.size)
        results.reconstructBeforeTerminatorEffect(state, block.terminator, terminatorLocation)
        visitor.visitTerminatorBeforePrimaryEffect(state, block.terminator, terminatorLocation)
        results.reconstructTerminatorEffect(state, block.terminator, terminatorLocation)
        visitor.visitTerminatorAfterPrimaryEffect(state, block.terminator, terminatorLocation)

        visitor.visitBlockEnd(state, block)
    }
}
