/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.borrowck

import org.rust.lang.core.mir.borrowck.MirReadKind.Borrow
import org.rust.lang.core.mir.borrowck.MirReadKind.Copy
import org.rust.lang.core.mir.borrowck.MirReadOrWrite.*
import org.rust.lang.core.mir.borrowck.MirWriteKind.*
import org.rust.lang.core.mir.dataflow.framework.BorrowCheckResults
import org.rust.lang.core.mir.dataflow.framework.BorrowData
import org.rust.lang.core.mir.dataflow.framework.BorrowSet
import org.rust.lang.core.mir.dataflow.framework.ResultsVisitor
import org.rust.lang.core.mir.dataflow.move.*
import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.mir.schemas.MirBorrowKind.*
import org.rust.lang.core.psi.ext.RsElement
import java.util.*

class MirBorrowCheckVisitor(
    private val body: MirBody,
    private val moveData: MoveData,

    // The set of borrows extracted from the MIR
    private val borrowSet: BorrowSet,

    // This keeps track of whether local variables are free-ed when the function exits even without a `StorageDead`,
    // which appears to be the case for constants.
    private val localsAreInvalidatedAtExit: Boolean
) : ResultsVisitor<BorrowCheckResults.State> {

    private val usesOfMovedValue: MutableSet<RsElement> = hashSetOf()
    private val usesOfUninitializedVariable: MutableSet<RsElement> = hashSetOf()
    private val moveOutWhileBorrowedValues: MutableSet<RsElement> = hashSetOf()

    val result: MirBorrowCheckResult
        get() = MirBorrowCheckResult(
            usesOfUninitializedVariable.toList(),
            usesOfMovedValue.toList(),
            moveOutWhileBorrowedValues.toList()
        )

    override fun visitStatementBeforePrimaryEffect(
        state: BorrowCheckResults.State,
        statement: MirStatement,
        location: MirLocation
    ) {
        checkActivations(location, state)

        when (statement) {
            is MirStatement.Assign -> {
                consumeRvalue(location, statement.rvalue, state)
                mutatePlace(location, statement.place, state)
            }

            is MirStatement.FakeRead -> {
                checkIfPathOrSubpathIsMoved(location, statement.place, state)
            }

            is MirStatement.StorageDead -> {
                // TODO accessPlace(location, statement.local, Write(StorageDeadOrDrop), state)
            }

            is MirStatement.StorageLive -> Unit
        }
    }

    override fun visitTerminatorBeforePrimaryEffect(
        state: BorrowCheckResults.State,
        terminator: MirTerminator<MirBasicBlock>,
        location: MirLocation
    ) {
        checkActivations(location, state)
    }

    override fun visitTerminatorAfterPrimaryEffect(
        state: BorrowCheckResults.State,
        terminator: MirTerminator<MirBasicBlock>,
        location: MirLocation
    ) {
        when (terminator) {
            is MirTerminator.Resume, is MirTerminator.Return -> {
                // Returning from the function implicitly kills storage for all locals and statics.
                // Often, the storage will already have been killed by an explicit StorageDead, but we don't always emit
                // those (notably on unwind paths), so this "extra check" serves as a kind of backup.
                for (borrow in borrowSet) {
                    if (!state.borrows[borrow.index]) continue
                    // TODO: checkForInvalidationAtExit
                }
            }

            else -> {}
        }
    }

    private fun mutatePlace(location: MirLocation, place: MirPlace, state: BorrowCheckResults.State) {
        // TODO: checkIfAssignedPathIsMoved
        accessPlace(location, place, Write(Mutate), state)
    }

    private fun consumeRvalue(location: MirLocation, rvalue: MirRvalue, state: BorrowCheckResults.State) {
        val implLenAndDiscriminant = { place: MirPlace ->
            accessPlace(location, place, Read(Copy), state)
            checkIfPathOrSubpathIsMoved(location, place, state)
        }
        when (rvalue) {
            is MirRvalue.Ref -> {
                val readOrWrite = when (val borrowKind = rvalue.borrowKind) {
                    Shallow -> Read(Borrow(borrowKind))
                    Shared -> Read(Borrow(borrowKind))
                    Unique, is Mut -> {
                        val writeKind = MutableBorrow(borrowKind)
                        if (borrowKind.allowTwoPhaseBorrow) {
                            Reservation(writeKind)
                        } else {
                            Write(writeKind)
                        }
                    }
                }

                accessPlace(location, rvalue.place, readOrWrite, state)
                checkIfPathOrSubpathIsMoved(location, rvalue.place, state)
            }

            is MirRvalue.AddressOf -> TODO()

            is MirRvalue.ThreadLocalRef -> Unit

            is MirRvalue.Use -> consumeOperand(location, rvalue.operand, state)
            is MirRvalue.UnaryOpUse -> consumeOperand(location, rvalue.operand, state)

            is MirRvalue.BinaryOpUse -> {
                consumeOperand(location, rvalue.left, state)
                consumeOperand(location, rvalue.right, state)
            }

            is MirRvalue.CheckedBinaryOpUse -> {
                consumeOperand(location, rvalue.left, state)
                consumeOperand(location, rvalue.right, state)
            }

            is MirRvalue.NullaryOpUse -> {
                // nullary ops take no dynamic input; no borrowck effect.
            }

            is MirRvalue.Repeat -> consumeOperand(location, rvalue.operand, state)

            is MirRvalue.Aggregate -> {
                when (rvalue) {
                    is MirRvalue.Aggregate.Adt, is MirRvalue.Aggregate.Array, is MirRvalue.Aggregate.Tuple -> Unit
                    // is MirRvalue.Aggregate.Closure -> propagateClosureUsedMutUpvar()
                }
                for (operand in rvalue.operands) {
                    consumeOperand(location, operand, state)
                }
            }

            is MirRvalue.CopyForDeref -> TODO()

            is MirRvalue.Len -> implLenAndDiscriminant(rvalue.place)
            is MirRvalue.Discriminant -> implLenAndDiscriminant(rvalue.place)

            is MirRvalue.Cast -> consumeOperand(location, rvalue.operand, state)
        }
    }

    private fun consumeOperand(location: MirLocation, operand: MirOperand, state: BorrowCheckResults.State) {
        when (operand) {
            is MirOperand.Constant -> Unit
            is MirOperand.Copy -> {
                accessPlace(location, operand.place, Read(Copy), state)
                checkIfPathOrSubpathIsMoved(location, operand.place, state)
            }

            is MirOperand.Move -> {
                accessPlace(location, operand.place, Write(Move), state)
                checkIfPathOrSubpathIsMoved(location, operand.place, state)
            }
        }
    }

    private fun accessPlace(
        location: MirLocation,
        place: MirPlace,
        readOrWrite: MirReadOrWrite,
        state: BorrowCheckResults.State
    ) {
        checkAccessForConflict(location, place, readOrWrite, state)
    }

    private fun checkAccessForConflict(
        location: MirLocation,
        place: MirPlace,
        readOrWrite: MirReadOrWrite,
        state: BorrowCheckResults.State
    ) {
        for (borrow in borrowSet) {
            if (!state.borrows[borrow.index]) continue
            // TODO: if (!borrowConflictsWithPlace()) continue

            when {
                // Obviously an activation is compatible with its own reservation (or even prior activating uses of same
                // borrow); so don't check if they interfere.
                //
                // NOTE: *reservations* do conflict with themselves; thus aren't injecting unsoundness w/ this check.)
                readOrWrite is Activation && readOrWrite.borrow == borrow -> {
                    continue
                }

                (readOrWrite is Read
                    && (borrow.kind == Shared
                    || borrow.kind == Shallow))
                    || (readOrWrite is Read
                    && readOrWrite.kind is Borrow
                    && readOrWrite.kind.kind == Shallow
                    && (borrow.kind == Unique
                    || borrow.kind is Mut)) -> {
                    continue
                }

                readOrWrite is Reservation
                    && (borrow.kind == Shallow
                    || borrow.kind == Shared) -> {
                    // This used to be a future compatibility warning (to be disallowed on NLL).
                    // See rust-lang/rust#56254
                    continue
                }

                readOrWrite is Write
                    && readOrWrite.kind is Move
                    && borrow.kind == Shallow -> {
                    // Handled by initialization checks.
                    continue
                }

                readOrWrite is Read
                    && (borrow.kind == Unique
                    || borrow.kind is Mut) -> {
                    // TODO: check `!isActive`

                    when (readOrWrite.kind) {
                        Copy -> Unit // TODO: reportUseWhileMutablyBorrowed
                        is Borrow -> Unit // TODO: reportConflictingBorrow
                    }

                    continue
                }

                readOrWrite is Activation
                    || readOrWrite is Reservation
                    || readOrWrite is Write -> {

                    val kind = when (readOrWrite) {
                        is Activation -> readOrWrite.kind
                        is Reservation -> readOrWrite.kind
                        is Write -> readOrWrite.kind
                        else -> error("impossible")
                    }

                    when (kind) {
                        is MutableBorrow -> Unit // TODO: reportConflictingBorrow
                        StorageDeadOrDrop -> Unit // TODO: reportStorageDeadOrDropOfBorrowed
                        Mutate -> Unit // TODO: reportIllegalMutationOfBorrowed
                        Move -> reportMoveOutWhileBorrowed(location)
                    }
                }
            }
        }
    }

    private fun reportMoveOutWhileBorrowed(location: MirLocation) {
        val element = location.source.span.reference
        if (element is RsElement) {
            moveOutWhileBorrowedValues += element
        }
    }

    private fun checkActivations(location: MirLocation, state: BorrowCheckResults.State) {
        // Two-phase borrow support: For each activation that is newly generated at this statement, check if it
        // interferes with another borrow.
        for (borrow in borrowSet.activationsAtLocation(location)) {
            when (borrow.kind) {
                Shallow, Shared -> error("only mutable borrows should be 2-phase")
                Unique, is Mut -> {}
            }

            accessPlace(location, borrow.borrowedPlace, Activation(MutableBorrow(borrow.kind), borrow), state)

            // We do not need to call `checkIfPathOrSubpathIsMoved` again, as we already called it when we made
            // the initial reservation.
        }
    }

    private fun checkIfFullPathIsMoved(location: MirLocation, place: MirPlace, state: BorrowCheckResults.State) {
        val maybeUninits = state.uninits
        val movePath = movePathClosestTo(place)
        if (maybeUninits[movePath.index]) {
            reportUseOfMovedOrUninitialized(location, movePath.place, place, movePath)
        }
    }

    private fun checkIfPathOrSubpathIsMoved(location: MirLocation, place: MirPlace, state: BorrowCheckResults.State) {
        checkIfFullPathIsMoved(location, place, state)

        // TODO MirProjectionElem.Subslice

        val movePath = movePathForPlace(place) ?: return
        val uninitMovePath = movePath.findInMovePathOrItsDescendants {
            state.uninits[it.index]
        } ?: return
        reportUseOfMovedOrUninitialized(location, place, place, uninitMovePath)
    }

    private fun movePathClosestTo(place: MirPlace): MovePath =
        when (val result = moveData.revLookup.find(place)) {
            is LookupResult.Exact -> result.movePath
            is LookupResult.Parent -> result.movePath ?: error("should have move path for every Local")
        }

    /**
     * If returns `null`, then there is no move path corresponding to a direct owner of `place`
     * (which means there is nothing that borrowck tracks for its analysis).
     */
    private fun movePathForPlace(place: MirPlace): MovePath? =
        when (val result = moveData.revLookup.find(place)) {
            is LookupResult.Exact -> result.movePath
            is LookupResult.Parent -> null
        }

    private fun reportUseOfMovedOrUninitialized(
        location: MirLocation,
        movedPlace: MirPlace,
        usedPlace: MirPlace,
        movePath: MovePath
    ) {
        val moveOutIndices = getMovedIndexes(location, movePath)
        val element = location.source.span.reference
        if (element is RsElement) {
            if (moveOutIndices.isEmpty()) {
                usesOfUninitializedVariable += element
            } else {
                usesOfMovedValue += element
            }
        }
    }

    private fun getMovedIndexes(location: MirLocation, movePath: MovePath): List<MoveOut> {
        fun predecessorLocations(body: MirBody, location: MirLocation): List<MirLocation> {
            return if (location.statementIndex == 0) {
                body.getBasicBlocksPredecessors()[location.block]
                    .map { it.terminatorLocation }
            } else {
                listOf(MirLocation(location.block, location.statementIndex - 1))
            }
        }

        val movePaths = movePath.ancestors.toList()

        val stack = ArrayDeque<MirLocation>()
        val backEdgeStack = ArrayDeque<MirLocation>()
        for (predecessor in predecessorLocations(body, location)) {
            val dominates = false  // TODO `location.dominates(predecessor)`
            if (dominates) {
                backEdgeStack.push(predecessor)
            } else {
                stack.push(predecessor)
            }
        }

        var reachedStart = false

        /* Check if the mpi is initialized as an argument */
        val isArgument =
            body.args.any { movePaths.contains(moveData.revLookup.find(it)) }

        val visited = linkedSetOf<MirLocation>()
        val result = mutableListOf<MoveOut>()
        fun dfsIter(location: MirLocation): Boolean {
            if (!visited.add(location)) return true

            // check for moves
            if (location.statement !is MirStatement.StorageDead) {
                // this analysis only tries to find moves explicitly written by the user,
                // so we ignore the move-outs created by `StorageDead` and at the beginning of a function

                for (moveOut in moveData.locMap[location].orEmpty()) {
                    if (moveOut.path in movePaths) {
                        result += moveOut
                        return true
                    }
                }
            }

            // check for inits
            var anyMatch = false
            for (init in moveData.initLocMap[location].orEmpty()) {
                when (init.kind) {
                    InitKind.Deep, InitKind.NonPanicPathOnly -> {
                        if (init.path in movePaths) {
                            anyMatch = true
                        }
                    }

                    InitKind.Shallow -> {
                        if (movePath == init.path) {
                            anyMatch = true
                        }
                    }
                }
            }
            return anyMatch
        }

        while (stack.isNotEmpty()) {
            val location1 = stack.pop()
            if (dfsIter(location1)) continue

            var hasPredecessor = false
            for (predecessor in predecessorLocations(body, location1)) {
                val dominates = false  // TODO `location1.dominates(predecessor)`
                if (dominates) {
                    backEdgeStack.push(predecessor)
                } else {
                    stack.push(predecessor)
                }
                hasPredecessor = true
            }

            if (!hasPredecessor) {
                reachedStart = true
            }
        }

        // Process back edges (moves in future loop iterations) only if
        // the move path is definitely initialized upon loop entry
        if ((isArgument || !reachedStart) && result.isEmpty()) {
            while (backEdgeStack.isNotEmpty()) {
                val location1 = backEdgeStack.pop()
                if (dfsIter(location1)) continue

                for (predecessor in predecessorLocations(body, location1)) {
                    backEdgeStack.push(location1)
                }
            }
        }

        return result
    }
}

/** Kind of access to a value: read or write (For informational purposes only) */
sealed class MirReadOrWrite {
    /** From the RFC: "A *read* means that the existing data may be read, but will not be changed." */
    data class Read(val kind: MirReadKind) : MirReadOrWrite()

    /**
     * From the RFC: "A *write* means that the data may be mutated to new values or otherwise invalidated (for example,
     * it could be de-initialized, as in a move operation).
     */
    data class Write(val kind: MirWriteKind) : MirReadOrWrite()

    /**
     * For two-phase borrows, we distinguish a reservation (which is treated like a Read) from an activation (which is
     * treated like a write), and each of those is furthermore distinguished from Reads/Writes above.
     */
    data class Reservation(val kind: MirWriteKind) : MirReadOrWrite()

    data class Activation(val kind: MirWriteKind, val borrow: BorrowData) : MirReadOrWrite()
}

/** Kind of read access to a value (For informational purposes only) */
sealed class MirReadKind {
    data class Borrow(val kind: MirBorrowKind) : MirReadKind()
    object Copy : MirReadKind()
}

/** Kind of write access to a value (For informational purposes only) */
sealed class MirWriteKind {
    object StorageDeadOrDrop : MirWriteKind()
    data class MutableBorrow(val kind: MirBorrowKind) : MirWriteKind()
    object Mutate : MirWriteKind()
    object Move : MirWriteKind()
}
