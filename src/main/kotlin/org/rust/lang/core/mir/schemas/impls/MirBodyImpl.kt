/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas.impls

import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.psi.ext.RsElement

data class MirBodyImpl(
    override val sourceElement: RsElement,
    override val basicBlocks: MutableList<MirBasicBlockImpl>,
    override val localDecls: List<MirLocal>,
    override val span: MirSpan,
    override val sourceScopes: List<MirSourceScope>,
    override val argCount: Int,
    override val varDebugInfo: List<MirVarDebugInfo>,
) : MirBody
