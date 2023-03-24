/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas.impls

import org.rust.lang.core.mir.schemas.MirBody
import org.rust.lang.core.mir.schemas.MirLocal
import org.rust.lang.core.mir.schemas.MirSourceInfo

class MirBodyImpl(
    override val basicBlocks: MutableList<MirBasicBlockImpl>,
    override val localDecls: List<MirLocal>,
    override val source: MirSourceInfo,
) : MirBody {
    override fun toString() = "MirBodyImpl(basicBlocks=$basicBlocks, localDecls=$localDecls, source=$source)"
}
