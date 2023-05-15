/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.framework

import java.util.*

interface GenKillAnalysis : Analysis<BitSet> {
    override fun copyState(state: BitSet): BitSet = state.clone() as BitSet

    override fun join(state1: BitSet, state2: BitSet): Boolean {
        // TODO optimize, do not copy
        val old = state1.clone()
        state1.or(state2)
        return state1 != old
    }
}
