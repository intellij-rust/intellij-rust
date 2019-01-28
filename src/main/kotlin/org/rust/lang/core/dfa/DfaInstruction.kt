/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa

import org.rust.lang.core.cfg.CFGNode

data class DfaInstruction(val priority: Int, val node: CFGNode, val state: DfaMemoryState, val id: Int) {
    override fun toString(): String = "$id:$priority:${node.index}:$state"

    companion object {
        fun fromNode(id: Int, node: CFGNode, state: DfaMemoryState): DfaInstruction = DfaInstruction(node.index, node, state, id)
        fun fromPreviousInstruction(
            instruction: DfaInstruction,
            node: CFGNode,
            state: DfaMemoryState = instruction.state,
            id: Int = instruction.id
        ): DfaInstruction = DfaInstruction(node.index + instruction.priority, node, state, id)
    }
}
