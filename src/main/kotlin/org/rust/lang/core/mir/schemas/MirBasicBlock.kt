/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.mir.WithIndex

interface MirBasicBlock : WithIndex {
    override val index: Int
    val statements: List<MirStatement>
    val terminator: MirTerminator<MirBasicBlock>
    val unwind: Boolean

    val terminatorLocation: MirLocation
        get() = MirLocation(this, statements.size)
}
