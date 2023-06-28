/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas.impls

import org.rust.lang.core.mir.schemas.MirBasicBlock
import org.rust.lang.core.mir.schemas.MirSwitchTargets

class MirSwitchTargetsImpl<BB : MirBasicBlock>(
    override val values: List<Long>,
    override val targets: List<BB>
) : MirSwitchTargets<BB> {
    companion object {
        fun <BB : MirBasicBlock> new(valuesAndTargets: List<Pair<Long, BB>>, otherwise: BB): MirSwitchTargetsImpl<BB> {
            val (values, targets) = valuesAndTargets.unzip()
            return MirSwitchTargetsImpl(values, targets + otherwise)
        }

        fun <BB : MirBasicBlock> `if`(value: Long, thenBlock: BB, elseBlock: BB): MirSwitchTargetsImpl<BB> {
            return MirSwitchTargetsImpl(listOf(value), listOf(thenBlock, elseBlock))
        }
    }
}
