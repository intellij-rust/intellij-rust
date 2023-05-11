/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.framework

import java.util.*

interface GenKillAnalysis : Analysis<BitSet> {
    override fun copyState(state: BitSet): BitSet = state.clone() as BitSet
}
