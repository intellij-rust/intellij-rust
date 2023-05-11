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

    fun applyStatementEffect(state: Domain, statement: MirStatement, location: MirLocation)
    fun applyTerminatorEffect(state: Domain, terminator: MirTerminator<MirBasicBlock>, location: MirLocation)

    fun intoEngine(body: MirBody): Engine<Domain> = Engine(body, this)
}
