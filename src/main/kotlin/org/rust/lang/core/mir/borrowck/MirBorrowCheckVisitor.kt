/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.borrowck

import org.rust.lang.core.mir.dataflow.framework.BorrowCheckResults
import org.rust.lang.core.mir.dataflow.framework.ResultsVisitor
import org.rust.lang.core.mir.dataflow.move.*
import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.psi.ext.RsElement
import java.util.*

class MirBorrowCheckVisitor(
    private val body: MirBody,
    private val moveData: MoveData,
) : ResultsVisitor<BorrowCheckResults.State> {

    private val usesOfMovedValue: MutableSet<RsElement> = hashSetOf()
    private val usesOfUninitializedVariable: MutableSet<RsElement> = hashSetOf()

    val result: MirBorrowCheckResult
        get() = MirBorrowCheckResult(usesOfMovedValue.toList(), usesOfUninitializedVariable.toList())

    override fun visitStatementBeforePrimaryEffect(
        state: BorrowCheckResults.State,
        statement: MirStatement,
        location: MirLocation
    ) {
        when (statement) {
            is MirStatement.Assign -> {
                consumeRvalue(location, statement.rvalue, state)
                /* TODO */
            }
            is MirStatement.FakeRead -> {
                checkIfPathOrSubpathIsMoved(location, statement.place, state)
            }
            is MirStatement.StorageDead -> {
                /* TODO accessPlace */
            }
            is MirStatement.StorageLive -> Unit
        }
    }

    private fun consumeRvalue(location: MirLocation, rvalue: MirRvalue, state: BorrowCheckResults.State) {
        when (rvalue) {
            is MirRvalue.Ref -> {
                /* TODO accessPlace */
                checkIfPathOrSubpathIsMoved(location, rvalue.place, state)
            }

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

            is MirRvalue.Aggregate -> {
                when (rvalue) {
                    is MirRvalue.Aggregate.Adt, is MirRvalue.Aggregate.Tuple -> Unit
                    // is MirRvalue.Aggregate.Closure -> propagateClosureUsedMutUpvar()
                }
                for (operand in rvalue.operands) {
                    consumeOperand(location, operand, state)
                }
            }
        }
    }

    private fun consumeOperand(location: MirLocation, operand: MirOperand, state: BorrowCheckResults.State) {
        when (operand) {
            is MirOperand.Constant -> Unit
            is MirOperand.Copy -> {
                /* TODO accessPlace */
                checkIfPathOrSubpathIsMoved(location, operand.place, state)
            }
            is MirOperand.Move -> {
                /* TODO accessPlace */
                checkIfPathOrSubpathIsMoved(location, operand.place, state)
            }
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

        // TODO subpath
    }

    private fun movePathClosestTo(place: MirPlace): MovePath =
        when (val result = moveData.revLookup.find(place)) {
            is LookupResult.Exact -> result.movePath
            is LookupResult.Parent -> result.movePath ?: error("should have move path for every Local")
        }

    private fun reportUseOfMovedOrUninitialized(
        location: MirLocation,
        movedPlace: MirPlace,
        usedPlace: MirPlace,
        movePath: MovePath
    ) {
        val moveOutIndices = getMovedIndexes(location, movePath)
        val element = location.source.span.reference
        if (moveOutIndices.isEmpty()) {
            usesOfUninitializedVariable += element
        } else {
            usesOfMovedValue += element
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
        val isArgument = false  // TODO

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
