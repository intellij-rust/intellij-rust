/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.borrowck.gatherLoans

import org.rust.lang.core.psi.RsPat
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.borrowck.*
import org.rust.lang.core.types.infer.Cmt
import org.rust.lang.core.types.infer.MemoryCategorizationContext
import org.rust.lang.core.types.type

class GatherLoanContext(private val bccx: BorrowCheckContext, private val moveData: MoveData = MoveData()) : Delegate {
    private val gmcx = GatherMoveContext(bccx, moveData)

    override fun consume(element: RsElement, cmt: Cmt, mode: ConsumeMode) {
        if (mode is ConsumeMode.Move) gmcx.gatherMoveFromExpr(element, cmt, mode.reason)
    }

    override fun matchedPat(pat: RsPat, cmt: Cmt, mode: MatchMode) {}

    override fun consumePat(pat: RsPat, cmt: Cmt, mode: ConsumeMode) {
        if (mode is ConsumeMode.Move) gmcx.gatherMoveFromPat(pat, cmt)
    }

    override fun declarationWithoutInit(binding: RsPatBinding) {
        gmcx.gatherDeclaration(binding, binding.type)
    }

    override fun mutate(assignmentElement: RsElement, assigneeCmt: Cmt, mode: MutateMode) {
        guaranteeAssignmentValid(assignmentElement, assigneeCmt, mode)
    }

    override fun useElement(element: RsElement, cmt: Cmt) {}

    /** Guarantees that [cmt] is assignable, or reports an error */
    private fun guaranteeAssignmentValid(assignment: RsElement, cmt: Cmt, mode: MutateMode) {
        // `loanPath` may be null with e.g. `*foo() = 5`
        // In such cases, there is no need to check for conflicts with moves etc, just ignore
        val loanPath = LoanPath.computeFor(cmt) ?: return
        // Some mutability and aliasability checks will be there (not implemented yet)
        gmcx.gatherAssignment(loanPath, assignment, cmt.element, mode)
    }

    fun check(): MoveData {
        val visitor = ExprUseWalker(this, MemoryCategorizationContext(bccx.implLookup, bccx.inference))
        visitor.consumeBody(bccx.body)
        return moveData
    }
}
