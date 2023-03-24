/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas.impls

import org.rust.lang.core.mir.schemas.MirBasicBlock
import org.rust.lang.core.mir.schemas.MirSwitchTargets

class MirSwitchTargetsImpl<BB : MirBasicBlock>(
    override val values: MutableList<Long>,
    override val targets: MutableList<BB>
) : MirSwitchTargets<BB> {
    companion object {
        fun <BB : MirBasicBlock> `if`(value: Long, thenBlock: BB, elseBlock: BB): MirSwitchTargetsImpl<BB> {
            return MirSwitchTargetsImpl(mutableListOf(value), mutableListOf(thenBlock, elseBlock))
        }
    }
}
