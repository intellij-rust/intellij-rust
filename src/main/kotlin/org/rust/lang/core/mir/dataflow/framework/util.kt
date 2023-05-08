/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.dataflow.framework

import org.rust.lang.core.mir.WithIndex
import org.rust.lang.core.mir.schemas.MirBasicBlock
import org.rust.lang.core.mir.schemas.MirBody
import org.rust.stdext.nextOrNull
import java.util.*

class WorkQueue<T: WithIndex>(size: Int) {
    private val deque: ArrayDeque<T> = ArrayDeque(size)
    private val set: BitSet = BitSet(size)

    fun insert(element: T) {
        if (set.add(element.index)) {
            deque.push(element)
        }
    }

    fun pop(): T {
        val element = deque.pop()
        set[element.index] = false
        return element
    }

    fun isEmpty(): Boolean = deque.isEmpty()
}

fun MirBody.getBasicBlocksInPostOrder(): List<MirBasicBlock> {
    val visited = BitSet(basicBlocks.size)
    val stack = ArrayDeque<Pair<MirBasicBlock, Iterator<MirBasicBlock>>>()
    val result = mutableListOf<MirBasicBlock>()
    val pushNode = { node: MirBasicBlock ->
        if (visited.add(node.index)) stack.push(Pair(node, node.terminator.successors.iterator()))
    }

    pushNode(basicBlocks.first())
    while (stack.isNotEmpty()) {
        val (node, iterator) = stack.pop()
        val target = iterator.nextOrNull()
        if (target != null) {
            stack.push(Pair(node, iterator))
            pushNode(target)
        } else {
            result.add(node)
        }
    }
    return result
}

fun BitSet.add(element: Int): Boolean =
    if (get(element)) {
        false
    } else {
        set(element)
        true
    }
