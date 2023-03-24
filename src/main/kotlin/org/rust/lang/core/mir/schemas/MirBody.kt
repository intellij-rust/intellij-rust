/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

interface MirBody {
    val basicBlocks: List<MirBasicBlock>
    val localDecls: List<MirLocal>
    val source: MirSourceInfo

    fun returnPlace(): MirLocal = localDecls.first()

    fun alwaysStorageLiveLocals(): Set<MirLocal> {
        return localDecls.toMutableSet().apply {
            basicBlocks.forEach { block ->
                block.statements.forEach { statement ->
                    when (statement) {
                        is MirStatement.StorageDead -> remove(statement.local)
                        is MirStatement.StorageLive -> remove(statement.local)
                        else -> Unit
                    }
                }
            }
        }
    }
}
