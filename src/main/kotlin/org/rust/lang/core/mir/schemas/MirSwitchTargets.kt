/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

interface MirSwitchTargets<BB : MirBasicBlock> : Iterable<Pair<Long, BB>> {
    val values: List<Long>
    val targets: List<BB>

    val otherwise: BB get() = targets.last()

    override fun iterator(): Iterator<Pair<Long, BB>> {
        return (values.asSequence() zip targets.asSequence()).iterator()
    }
}
