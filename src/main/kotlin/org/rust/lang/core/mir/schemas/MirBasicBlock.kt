/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

interface MirBasicBlock {
    val statements: List<MirStatement>
    val terminator: MirTerminator<MirBasicBlock>
    val unwind: Boolean
}
