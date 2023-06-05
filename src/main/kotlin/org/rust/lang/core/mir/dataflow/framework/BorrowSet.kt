/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.framework

import org.rust.lang.core.mir.WithIndex
import org.rust.lang.core.mir.dataflow.impls.ignoreBorrow
import org.rust.lang.core.mir.dataflow.move.MoveData
import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.mir.util.IndexAlloc
import org.rust.lang.core.mir.util.IndexKeyMap
import java.util.*

class BorrowSet private constructor(
    /**
     * The fundamental map relating bitvector indexes to the borrows in the MIR. Each borrow is also uniquely identified
     * in the MIR by the [MirLocation] of the assignment statement in which it appears on the right hand side. Thus the
     * location is the map key, and its position in the map corresponds to `BorrowData`.
     */
    val locationMap: MutableMap<MirLocation, BorrowData>,

    /**
     * Locations which activate borrows.
     * NOTE: a given location may activate more than one borrow in the future when more general two-phase borrow support
     * is introduced, but for now we only need to store one borrow index.
     */
    private val activationMap: MutableMap<MirLocation, MutableList<BorrowData>>,

    /** Map from local to all the borrows on that local. */
    val localMap: IndexKeyMap<MirLocal, MutableSet<BorrowData>>,

    val localsStateAtExit: LocalsStateAtExit,

    private val borrowData: IndexAlloc<BorrowData>
) : Iterable<BorrowData> {
    val size: Int get() = borrowData.size

    override fun iterator(): Iterator<BorrowData> = locationMap.values.iterator()

    fun activationsAtLocation(location: MirLocation): List<BorrowData> {
        return activationMap[location].orEmpty()
    }

    companion object {
        fun build(
            body: MirBody,
            localsAreInvalidatedAtExit: Boolean,
            moveData: MoveData
        ): BorrowSet {
            val visitor = GatherBorrows(
                body,
                mutableMapOf(),
                mutableMapOf(),
                IndexKeyMap(),
                IndexKeyMap(),
                LocalsStateAtExit.build(localsAreInvalidatedAtExit, body, moveData),
                IndexAlloc()
            )

            for (block in body.getBasicBlocksInPreOrder()) {
                visitor.visitBasicBlock(block)
            }

            return BorrowSet(
                visitor.locationMap,
                visitor.activationMap,
                visitor.localMap,
                visitor.localsStateAtExit,
                visitor.borrowData
            )
        }
    }
}

sealed class TwoPhaseActivation {
    object NotTwoPhase : TwoPhaseActivation()
    object NotActivated : TwoPhaseActivation()
    data class ActivatedAt(val location: MirLocation) : TwoPhaseActivation()
}

class BorrowData(
    override val index: Int,
    /**
     * Location where the borrow reservation starts.
     * In many cases, this will be equal to the activation location but not always.
     */
    val reserveLocation: MirLocation,
    /** Location where the borrow is activated */
    var activationLocation: TwoPhaseActivation,
    /** What kind of borrow this is */
    val kind: MirBorrowKind,
    /** Place from which we are borrowing */
    val borrowedPlace: MirPlace,
    /** Place to which the borrow was stored */
    val assignedPlace: MirPlace
) : WithIndex

sealed class LocalsStateAtExit {
    object AllAreInvalidated : LocalsStateAtExit()
    data class SomeAreInvalidated(val hasStorageDeadOrMoved: BitSet) : LocalsStateAtExit()

    companion object {
        fun build(
            localsAreInvalidatedAtExit: Boolean,
            body: MirBody,
            moveData: MoveData
        ): LocalsStateAtExit {
            if (localsAreInvalidatedAtExit) return AllAreInvalidated

            val hasStorageDeadOrMoved = BitSet(body.localDecls.size)

            object : MirVisitor {
                override fun returnPlace(): MirLocal = body.returnPlace()
                override fun visitLocal(local: MirLocal, context: MirPlaceContext, location: MirLocation) {
                    if (context is MirPlaceContext.NonUse.StorageDead) {
                        hasStorageDeadOrMoved[local.index] = true
                    }
                }
            }.visitBody(body)

            for (moveOut in moveData.locMap.values.flatten()) { // TODO: attention
                val index = moveData.baseLocal(moveOut.path)?.index ?: continue
                hasStorageDeadOrMoved[index] = true
            }

            return SomeAreInvalidated(hasStorageDeadOrMoved)
        }
    }
}

class GatherBorrows(
    val body: MirBody,
    val locationMap: MutableMap<MirLocation, BorrowData>,
    val activationMap: MutableMap<MirLocation, MutableList<BorrowData>>,
    val localMap: IndexKeyMap<MirLocal, MutableSet<BorrowData>>,

    /**
     * When we encounter a 2-phase borrow statement, it will always be assigning into a temporary TEMP:
     *
     * TEMP = &foo
     *
     * We add TEMP into this map with `b`, where `b` is the index of the borrow. When we find a later use of this
     * activation, we remove from the map (and add to the "tombstone" set below).
     */
    private val pendingActivations: IndexKeyMap<MirLocal, BorrowData>,

    val localsStateAtExit: LocalsStateAtExit,
    val borrowData: IndexAlloc<BorrowData>
) : MirVisitor {

    override fun returnPlace(): MirLocal = body.returnPlace()

    override fun visitAssign(place: MirPlace, rvalue: MirRvalue, location: MirLocation) {
        if (rvalue is MirRvalue.Ref) {
            if (rvalue.place.ignoreBorrow(localsStateAtExit)) return
            val borrow = borrowData.allocate { index ->
                BorrowData(
                    index,
                    location,
                    TwoPhaseActivation.NotTwoPhase,
                    rvalue.borrowKind,
                    rvalue.place,
                    place
                )
            }
            val borrowFromMap = locationMap.putIfAbsent(location, borrow) ?: borrow
            insertAsPendingIfTwoPhase(place, rvalue.borrowKind, borrowFromMap)
            localMap.getOrPut(rvalue.place.local) { mutableSetOf() }.add(borrowFromMap)
        }

        super.visitAssign(place, rvalue, location)
    }

    override fun visitLocal(local: MirLocal, context: MirPlaceContext, location: MirLocation) {
        if (!context.isUse) return

        // We found a use of some temporary TMP check whether we (earlier) saw a 2-phase borrow like
        //
        //     TMP = &mut place
        val borrowData = pendingActivations[local]
        if (borrowData != null) {
            // Watch out: the use of TMP in the borrow itself doesn't count as an activation. =)
            if (borrowData.reserveLocation == location && context is MirPlaceContext.MutatingUse.Store) return

            activationMap.getOrPut(location) { mutableListOf() }.add(borrowData)
            borrowData.activationLocation = TwoPhaseActivation.ActivatedAt(location)
        }
    }

    private fun insertAsPendingIfTwoPhase(assignedPlace: MirPlace, kind: MirBorrowKind, borrowData: BorrowData) {
        if (!kind.allowTwoPhaseBorrow) return

        // Consider the borrow not activated to start. When we find an activation, we'll update this field.
        borrowData.activationLocation = TwoPhaseActivation.NotActivated

        // Insert `local` into the list of pending activations. From now on, we'll be on the lookout for a use of it.
        // Note that we are guaranteed that this use will come after the assignment.
        pendingActivations[assignedPlace.local] = borrowData
    }
}
