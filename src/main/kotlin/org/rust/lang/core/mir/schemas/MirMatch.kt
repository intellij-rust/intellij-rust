/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.mir.building.PlaceBuilder
import org.rust.lang.core.mir.schemas.impls.MirBasicBlockImpl
import org.rust.lang.core.thir.LocalVar
import org.rust.lang.core.thir.ThirBindingMode
import org.rust.lang.core.thir.ThirExpr
import org.rust.lang.core.thir.ThirPat
import org.rust.lang.core.types.regions.Scope

class MirArm(
    val pattern: ThirPat,
    val guard: Any?,
    val body: ThirExpr,
    val scope: Scope,
    val span: MirSpan,
)

class MirCandidate(
    val span: MirSpan,
    val hasGuard: Boolean,
    var matchPairs: MutableList<MirMatchPair>,
    var bindings: MutableList<MirBinding>,
    val subcandidates: List<MirCandidate>,
    var otherwiseBlock: MirBasicBlockImpl?,
    var preBindingBlock: MirBasicBlockImpl?,
    var nextCandidatePreBindingBlock: MirBasicBlockImpl?,
) {
    constructor(place: PlaceBuilder, pattern: ThirPat, hasGuard: Boolean) :
        this(
            span = pattern.source,
            hasGuard = hasGuard,
            matchPairs = mutableListOf(MirMatchPair.new(place, pattern)),
            bindings = mutableListOf(),
            subcandidates = listOf(),
            otherwiseBlock = null,
            preBindingBlock = null,
            nextCandidatePreBindingBlock = null
        )

    fun visitLeaves(callback: (MirCandidate) -> Unit) {
        if (subcandidates.isEmpty()) {
            callback(this)
        } else {
            TODO()
        }
    }
}

class MirMatchPair private constructor(
    // this place...
    val place: PlaceBuilder,
    // ... must match this pattern.
    val pattern: ThirPat,
) {
    companion object {
        fun new(place: PlaceBuilder, pattern: ThirPat): MirMatchPair {
            // TODO place.resolve_upvar
            // TODO may_need_cast
            return MirMatchPair(place, pattern)
        }
    }
}

class MirBinding(
    val span: MirSpan,
    val source: MirPlace,
    val variable: LocalVar,
    val bindingMode: ThirBindingMode,
)
