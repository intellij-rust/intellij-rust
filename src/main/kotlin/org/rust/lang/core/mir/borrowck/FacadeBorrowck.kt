/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.borrowck

import org.rust.lang.core.mir.dataflow.framework.BorrowCheckResults
import org.rust.lang.core.mir.dataflow.framework.BorrowSet
import org.rust.lang.core.mir.dataflow.framework.getBasicBlocksInPostOrder
import org.rust.lang.core.mir.dataflow.framework.visitResults
import org.rust.lang.core.mir.dataflow.impls.Borrows
import org.rust.lang.core.mir.dataflow.impls.MaybeUninitializedPlaces
import org.rust.lang.core.mir.dataflow.move.MoveData
import org.rust.lang.core.mir.schemas.MirBody
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsFunctionOrLambda
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.ancestorOrSelf

fun doMirBorrowCheck(body: MirBody): MirBorrowCheckResult {
    val moveData = MoveData.gatherMoves(body)
    val localsAreInvalidatedAtExit = body.sourceElement.ancestorOrSelf<RsItemElement>() is RsFunctionOrLambda
    val borrowSet = BorrowSet.build(body, localsAreInvalidatedAtExit, moveData)

    val borrows = Borrows(borrowSet, emptyMap())
        .intoEngine(body)
        .iterateToFixPoint()
    val uninitializedPlaces = MaybeUninitializedPlaces(moveData)
        .intoEngine(body)
        .iterateToFixPoint()

    val visitor = MirBorrowCheckVisitor(body, moveData, borrowSet, localsAreInvalidatedAtExit)
    val results = BorrowCheckResults(uninitializedPlaces, borrows)
    visitResults(results, body.getBasicBlocksInPostOrder(), visitor)
    return visitor.result
}

data class MirBorrowCheckResult(
    val usesOfUninitializedVariable: List<RsElement>,
    val usesOfMovedValue: List<RsElement>,
    val moveOutWhileBorrowedValues: List<RsElement>,
)
