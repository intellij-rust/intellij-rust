/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.borrowck

import org.rust.lang.core.cfg.ControlFlowGraph
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsInferenceContextOwner
import org.rust.lang.core.psi.ext.body
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.borrowck.gatherLoans.GatherLoanContext
import org.rust.lang.core.types.controlFlowGraph
import org.rust.lang.core.types.infer.Cmt
import org.rust.lang.core.types.infer.RsInferenceResult
import org.rust.lang.core.types.inference

class BorrowCheckContext private constructor(
    val inference: RsInferenceResult,
    val body: RsBlock,
    val cfg: ControlFlowGraph,
    val implLookup: ImplLookup = ImplLookup.relativeTo(body),
    private val usesOfMovedValue: MutableSet<UseOfMovedValueError> = hashSetOf(),
    private val usesOfUninitializedVariable: MutableSet<UseOfUninitializedVariable> = hashSetOf(),
    private val moveErrors: MutableSet<MoveError> = hashSetOf()
) {
    companion object {
        fun buildFor(owner: RsInferenceContextOwner): BorrowCheckContext? {
            // TODO: handle body represented by RsExpr
            val body = owner.body as? RsBlock ?: return null
            val cfg = owner.controlFlowGraph ?: return null
            return BorrowCheckContext(owner.inference, body, cfg)
        }
    }

    fun check(): BorrowCheckResult? {
        val data = buildAnalysisData(this)
        if (data != null) {
            val clcx = CheckLoanContext(this, data.moveData)
            clcx.checkLoans(body)
        }

        return BorrowCheckResult(
            this.usesOfMovedValue.toList(),
            this.usesOfUninitializedVariable.toList(),
            this.moveErrors.toList()
        )
    }

    private fun buildAnalysisData(bccx: BorrowCheckContext): AnalysisData? {
        val glcx = GatherLoanContext(this)
        val moveData = glcx.check().takeIf { it.isNotEmpty() } ?: return null
        val flowedMoves = FlowedMoveData.buildFor(moveData, bccx, cfg)
        return AnalysisData(flowedMoves)
    }

    fun reportUseOfMovedValue(loanPath: LoanPath, move: Move) {
        if (move.kind == MoveKind.Declared) {
            usesOfUninitializedVariable.add(UseOfUninitializedVariable(loanPath.element))
        } else {
            usesOfMovedValue.add(UseOfMovedValueError(loanPath.element, move))
        }
    }

    fun reportMoveError(from: Cmt) {
        moveErrors.add(MoveError(from))
    }
}

class AnalysisData(val moveData: FlowedMoveData)

data class BorrowCheckResult(
    val usesOfMovedValue: List<UseOfMovedValueError>,
    val usesOfUninitializedVariable: List<UseOfUninitializedVariable>,
    val moveErrors: List<MoveError>
)

data class UseOfMovedValueError(val use: RsElement, val move: Move)
data class UseOfUninitializedVariable(val use: RsElement)
data class MoveError(val from: Cmt)
