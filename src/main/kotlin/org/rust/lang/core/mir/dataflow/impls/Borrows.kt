/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.impls

import org.rust.lang.core.mir.dataflow.framework.*
import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.types.ty.TyPointer
import org.rust.lang.core.types.ty.TyReference
import java.util.*

class Borrows(
    private val borrowSet: BorrowSet,
    private val borrowsOutOfScopeAtLocation: Map<MirLocation, List<BorrowData>>
) : GenKillAnalysis {
    override val direction: Direction = Forward

    // bottom = nothing is reserved or activated yet
    override fun bottomValue(body: MirBody): BitSet = BitSet(borrowSet.size)

    override fun initializeStartBlock(body: MirBody, state: BitSet) {
        // no borrows of code region_scopes have been taken prior to function execution, so this method has no effect.
    }

    override fun applyBeforeStatementEffect(state: BitSet, statement: MirStatement, location: MirLocation) {
        killLoansOutOfScopeAtLocation(state, location)
    }

    override fun applyStatementEffect(state: BitSet, statement: MirStatement, location: MirLocation) {
        when (statement) {
            is MirStatement.Assign -> {
                val rhs = statement.rvalue
                if (rhs is MirRvalue.Ref) {
                    if (rhs.place.ignoreBorrow(borrowSet.localsStateAtExit)) return
                    val borrowData = borrowSet.locationMap[location] ?: return
                    state[borrowData.index] = true
                }

                // Make sure there are no remaining borrows for variables that are assigned over.
                killBorrowsOnPlace(state, statement.place)

            }

            is MirStatement.StorageDead -> {
                // Make sure there are no remaining borrows for locals that are gone out of scope.
                killBorrowsOnPlace(state, MirPlace(statement.local))
            }

            else -> {}
        }
    }

    override fun applyBeforeTerminatorEffect(state: BitSet, terminator: MirTerminator<MirBasicBlock>, location: MirLocation) {
        killLoansOutOfScopeAtLocation(state, location)
    }

    override fun applyTerminatorEffect(state: BitSet, terminator: MirTerminator<MirBasicBlock>, location: MirLocation) {
        // TODO: process TerminatorKind::InlineAsm
    }

    /**
     * Add all borrows to the kill set, if those borrows are out of scope at `location`.
     * That means they went out of a nonlexical scope
     */
    private fun killLoansOutOfScopeAtLocation(state: BitSet, location: MirLocation) {
        // NOTE: The state associated with a given `location` reflects the dataflow on entry to the statement.
        // Iterate over each of the borrows that we've precomputed to have went out of scope at this location and kill
        // them.
        //
        // We are careful always to call this function *before* we set up the gen-bits for the statement or terminator.
        // That way, if the effect of the statement or terminator *does* introduce a new loan of the same region, then
        // setting that gen-bit will override any potential kill introduced here.
        val borrows = borrowsOutOfScopeAtLocation[location] ?: return
        for (borrow in borrows) {
            state[borrow.index] = false
        }
    }

    /** Kill any borrows that conflict with [place]. */
    private fun killBorrowsOnPlace(state: BitSet, place: MirPlace) {
        val otherBorrowsOfLocal = borrowSet.localMap[place.local].orEmpty()

        // If the borrowed place is a local with no projections, all other borrows of this local must conflict.
        // This is purely an optimization, so we don't have to call `places_conflict` for every borrow.
        if (place.projections.isEmpty()) {
            if (!place.local.isRefToStatic) {
                for (borrow in otherBorrowsOfLocal) {
                    state[borrow.index] = false
                }
            }
            return
        }

        // By passing `PlaceConflictBias::NoOverlap`, we conservatively assume that any given pair of array indices are
        // not equal, so that when `placesСonflict` returns true, we will be assured that two places being compared
        // definitely denotes the same sets of locations.
        val definitelyConflictingBorrows = otherBorrowsOfLocal.filter { _ ->
            // TODO: placesConflict
            false
        }

        for (borrow in definitelyConflictingBorrows) {
            state[borrow.index] = false
        }
    }
}

fun MirPlace.ignoreBorrow(localsStateAtExit: LocalsStateAtExit): Boolean {
    // If a local variable is immutable, then we only need to track borrows to guard against two kinds of errors:
    // * The variable being dropped while still borrowed (e.g., because the fn returns a reference to a local variable)
    // * The variable being moved while still borrowed
    //
    // In particular, the variable cannot be mutated -- the "access checks" will fail -- so we don't have to worry about
    // mutation while borrowed.
    if (localsStateAtExit is LocalsStateAtExit.SomeAreInvalidated) {
        val ignore = !localsStateAtExit.hasStorageDeadOrMoved[local.index] && !local.mutability.isMut
        if (ignore) return true
    }

    // TODO: support projections when they appear in MIR
    val projection = projections.firstOrNull()
    if (projection is MirProjectionElem.Deref) {
        val ty = local.ty
        when {
            ty is TyReference && !ty.mutability.isMut -> {
                // For references to thread-local statics, we do need to track the borrow.
                if (!local.isRefToThreadLocal) return false
            }

            ty is TyPointer || ty is TyReference && !ty.mutability.isMut -> {
                // For both derefs of raw pointers and `&T` references, the original path is `Copy` and therefore
                // not significant. In particular, there is nothing the user can do to the original path that would
                // invalidate the newly created reference -- and if there were, then the user could have copied the
                // original path into a new variable and borrowed *that* one, leaving the original path unborrowed.
                return true
            }

            else -> {}
        }
    }

    return false
}
