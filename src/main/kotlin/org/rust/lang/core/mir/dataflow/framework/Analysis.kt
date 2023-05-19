/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.framework

import org.rust.lang.core.mir.schemas.*

interface Analysis<Domain> {
    val direction: Direction
    fun bottomValue(body: MirBody): Domain
    fun initializeStartBlock(body: MirBody, state: Domain)
    fun join(state1: Domain, state2: Domain): Boolean
    fun copyState(state: Domain): Domain

    fun applyBeforeStatementEffect(state: Domain, statement: MirStatement, location: MirLocation) {}
    fun applyStatementEffect(state: Domain, statement: MirStatement, location: MirLocation)
    fun applyBeforeTerminatorEffect(state: Domain, terminator: MirTerminator<MirBasicBlock>, location: MirLocation) {}
    fun applyTerminatorEffect(state: Domain, terminator: MirTerminator<MirBasicBlock>, location: MirLocation)

    /**
     * Updates the current dataflow state with the effect of a successful return from a [MirTerminator.Call].
     * This is separate from [applyTerminatorEffect] to properly track state across unwind edges.
     */
    fun applyCallReturnEffect(state: Domain, block: MirBasicBlock, returnPlace: MirPlace) {}

    fun intoEngine(body: MirBody): Engine<Domain> = Engine(body, this)
}
