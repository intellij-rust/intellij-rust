/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

data class MirLocation(
    val block: MirBasicBlock,
    val statementIndex: Int
) {

    val source: MirSourceInfo
        get() = when (statementIndex) {
            block.statements.size -> block.terminator.source
            else -> block.statements[statementIndex].source
        }

    /** null if corresponds to terminator */
    val statement: MirStatement?
        get() = block.statements.getOrNull(statementIndex)
}
