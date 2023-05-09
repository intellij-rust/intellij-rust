/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.borrowck

import org.rust.lang.core.mir.dataflow.framework.BorrowCheckResults
import org.rust.lang.core.mir.dataflow.framework.getBasicBlocksInPostOrder
import org.rust.lang.core.mir.dataflow.framework.visitResults
import org.rust.lang.core.mir.dataflow.impls.MaybeUninitializedPlaces
import org.rust.lang.core.mir.dataflow.move.MoveData
import org.rust.lang.core.mir.schemas.MirBody
import org.rust.lang.core.psi.ext.RsElement

fun doMirBorrowCheck(body: MirBody): MirBorrowCheckResult {
    val moveData = MoveData.gatherMoves(body)
    val uninitializedPlaces = MaybeUninitializedPlaces(moveData)
        .intoEngine(body)
        .iterateToFixPoint()

    val visitor = MirBorrowCheckVisitor(body, moveData)
    val results = BorrowCheckResults(uninitializedPlaces)
    visitResults(results, body.getBasicBlocksInPostOrder(), visitor)
    return visitor.result
}

data class MirBorrowCheckResult(
    val usesOfUninitializedVariable: List<RsElement>,
)
