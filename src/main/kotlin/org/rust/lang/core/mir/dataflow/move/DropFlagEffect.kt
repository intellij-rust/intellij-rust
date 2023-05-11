/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.move

import org.rust.lang.core.mir.schemas.MirLocation

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
    // TODO actually process children
    eachChild(movePath)
}
