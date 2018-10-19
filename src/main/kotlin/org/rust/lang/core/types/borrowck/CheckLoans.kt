/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.borrowck

import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsPat
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.borrowck.LoanPathElement.Interior
import org.rust.lang.core.types.borrowck.LoanPathKind.Downcast
import org.rust.lang.core.types.borrowck.LoanPathKind.Extend
import org.rust.lang.core.types.borrowck.gatherLoans.isAdtWithDestructor
import org.rust.lang.core.types.infer.Cmt
import org.rust.lang.core.types.infer.MemoryCategorizationContext
import org.rust.lang.core.types.inference

class CheckLoanContext(private val bccx: BorrowCheckContext, private val moveData: FlowedMoveData) : Delegate {
    override fun consume(element: RsElement, cmt: Cmt, mode: ConsumeMode) {
        consumeCommon(element, cmt)
    }

    override fun matchedPat(pat: RsPat, cmt: Cmt, mode: MatchMode) {}

    override fun consumePat(pat: RsPat, cmt: Cmt, mode: ConsumeMode) {
        consumeCommon(pat, cmt)
    }

    private fun checkIfPathIsMoved(element: RsElement, loanPath: LoanPath) {
        moveData.eachMoveOf(element, loanPath) { move, _ ->
            bccx.reportUseOfMovedValue(loanPath, move)
            false
        }
    }

    override fun declarationWithoutInit(element: RsElement) {}

    override fun mutate(assignmentElement: RsElement, assigneeCmt: Cmt, mode: MutateMode) {
        val loanPath = LoanPath.computeFor(assigneeCmt) ?: return
        when (mode) {
            MutateMode.Init, MutateMode.JustWrite -> {
                // In a case like `path = 1`, path does not have to be FULLY initialized, but we still
                // must be careful lest it contains derefs of pointers.
                checkIfAssignedPathIsMoved(assigneeCmt.element, loanPath)
            }
            MutateMode.WriteAndRead -> {
                // In a case like `path += 1`, path must be fully initialized, since we will read it before we write it
                checkIfPathIsMoved(assigneeCmt.element, loanPath)
            }
        }
    }

    fun checkLoans(body: RsBlock) {
        val mc = MemoryCategorizationContext(bccx.implLookup, bccx.owner.inference)
        ExprUseWalker(this, mc).consumeBody(body)
    }

    /**
     * Reports an error if assigning to [loanPath] will use a moved/uninitialized value.
     * Mainly this is concerned with detecting derefs of uninitialized pointers.
     */
    private fun checkIfAssignedPathIsMoved(element: RsElement, loanPath: LoanPath) {
        if (loanPath.kind is Downcast) {
            checkIfAssignedPathIsMoved(element, loanPath.kind.loanPath)
        }
        // assigning to `x` does not require that `x` is initialized, so process only `Extend`
        val extend = loanPath.kind as? Extend ?: return
        val baseLoanPath = extend.loanPath
        val lpElement = extend.lpElement

        if (lpElement is Interior.Field) {
            if (baseLoanPath.ty.isAdtWithDestructor) {
                moveData.eachMoveOf(element, baseLoanPath) { _, _ -> false }
            } else {
                checkIfAssignedPathIsMoved(element, baseLoanPath)
            }
        } else {
            checkIfPathIsMoved(element, baseLoanPath)
        }
    }

    private fun consumeCommon(element: RsElement, cmt: Cmt) {
        val loanPath = LoanPath.computeFor(cmt) ?: return
        checkIfPathIsMoved(element, loanPath)
    }
}
