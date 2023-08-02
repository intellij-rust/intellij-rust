/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.move

import org.rust.lang.core.dfa.borrowck.gatherLoans.hasDestructor
import org.rust.lang.core.mir.schemas.MirBody
import org.rust.lang.core.mir.schemas.MirLocation
import org.rust.lang.core.mir.schemas.MirPlace
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsStructKind
import org.rust.lang.core.psi.ext.kind
import org.rust.lang.core.types.ty.*

enum class DropFlagState {
    /** The tracked value is initialized and needs to be dropped when leaving its scope */
    Present,

    /** The tracked value is uninitialized or was moved out of and does not need to be dropped when leaving its scope */
    Absent,
}

fun dropFlagEffectsForLocation(
    moveData: MoveData,
    loc: MirLocation,
    callback: (MovePath, DropFlagState) -> Unit
) {
    for (mi in moveData.locMap[loc].orEmpty()) {
        onAllChildrenBits(mi.path) { mpi ->
            callback(mpi, DropFlagState.Absent)
        }
    }

    // TODO TerminatorKind::Drop

    forLocationInits(moveData, loc) { callback(it, DropFlagState.Present) }
}

fun forLocationInits(
    moveData: MoveData,
    loc: MirLocation,
    callback: (MovePath) -> Unit
) {
    for (init in moveData.initLocMap[loc].orEmpty()) {
        when (init.kind) {
            InitKind.Deep -> onAllChildrenBits(init.path, callback)
            InitKind.Shallow -> callback(init.path)
            InitKind.NonPanicPathOnly -> Unit
        }
    }
}

fun onAllChildrenBits(
    movePath: MovePath,
    eachChild: (MovePath) -> Unit
) {
    eachChild(movePath)

    if (isTerminalPath(movePath)) return

    var nextChild = movePath.firstChild
    while (nextChild != null) {
        onAllChildrenBits(nextChild, eachChild)
        nextChild = nextChild.nextSibling
    }
}

fun dropFlagEffectsForFunctionEntry(
    body: MirBody,
    moveData: MoveData,
    callback: (MovePath, DropFlagState) -> Unit,
) {
    body.args.forEach { arg ->
        val lookupResult = moveData.revLookup.find(MirPlace(arg))
        onLookupResultBits(lookupResult) {
            callback(it, DropFlagState.Present)
        }
    }
}

private fun onLookupResultBits(
    lookupResult: LookupResult,
    eachChild: (MovePath) -> Unit
) {
    when (lookupResult) {
        is LookupResult.Exact -> onAllChildrenBits(lookupResult.movePath, eachChild)
        is LookupResult.Parent -> Unit // access to untracked value - do not touch children
    }
}

private fun isTerminalPath(movePath: MovePath): Boolean {
    val place = movePath.place
    return when (val ty = place.ty().ty) {
        is TyAdt -> {
            val isUnion = (ty.item as? RsStructItem)?.kind == RsStructKind.UNION
            (ty.item.hasDestructor && !ty.isBox) || isUnion
        }
        is TySlice, is TyReference, is TyPointer -> true
        else -> false
    }
}
