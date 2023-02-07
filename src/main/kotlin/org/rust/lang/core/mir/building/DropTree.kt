/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.building

import org.rust.lang.core.mir.schemas.impls.MirBasicBlockImpl

class DropTree {
    val root: DropNode = DropNode.Root()
    private val entryPoints = mutableMapOf<DropNode, MutableList<MirBasicBlockImpl>>()

    fun addEntry(from: MirBasicBlockImpl, to: DropNode) {
        val list = entryPoints.computeIfAbsent(to) { mutableListOf() }
        list.add(from)
    }

    fun buildMir(dropTreeBuilder: DropTreeBuilder, first: MirBasicBlockImpl?): Map<DropNode, MirBasicBlockImpl> {
        val blocks: MutableMap<DropNode, MirBasicBlockImpl> = mutableMapOf()
        if (first != null) blocks[root] = first
        assignBlocks(dropTreeBuilder, blocks)
        // TODO: linkDrops. It will be needed soon I guess
        return blocks
    }

    private fun assignBlocks(dropTreeBuilder: DropTreeBuilder, blocks: MutableMap<DropNode, MirBasicBlockImpl>) {
        root.forEach { drop ->
            entryPoints[drop]?.let { dropBlocks ->
                val block = blocks.getOrPut(drop) { dropTreeBuilder.makeBlock() }
                dropBlocks.reversed().forEach { entryBlock ->
                    dropTreeBuilder.addEntry(entryBlock, block)
                }
            }
            // TODO: there is more
        }
    }

    fun addDrop(drop: Drop, next: DropNode): DropNode {
        val node = DropNode.Default(next, drop)
        next.previous.add(node)
        return node
    }

    // Iterates over itself and it's previous drops
    sealed class DropNode : Iterable<DropNode> {
        val previous = mutableListOf<DropNode>()

        override fun iterator(): Iterator<DropNode> {
            val previousSequence = previous.asSequence().flatMap { it.previous.asSequence() }
            return (sequenceOf(this) + previousSequence).iterator()
        }

        class Root : DropNode()
        class Default(val next: DropNode, val drop: Drop) : DropNode()
    }
}
