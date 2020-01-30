/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core

import org.rust.lang.core.FlowDirection.Backward
import org.rust.lang.core.FlowDirection.Forward
import org.rust.lang.core.cfg.CFGNode
import org.rust.lang.core.cfg.ControlFlowGraph
import org.rust.lang.core.psi.ext.RsElement
import java.util.*

enum class EntryOrExit { Entry, Exit }

enum class FlowDirection { Forward, Backward }

class DataFlowContext<O : DataFlowOperator>(
    private val cfg: ControlFlowGraph,
    private val oper: O,
    private val bitsPerElement: Int,
    private val flowDirection: FlowDirection
) {
    private val wordsPerElement: Int = (bitsPerElement + BITS_PER_INT - 1) / BITS_PER_INT
    private val gens: MutableList<Int> // TODO: use something like bit array to improve performance
    private val scopeKills: MutableList<Int>
    private val actionKills: MutableList<Int>
    private val onEntry: MutableList<Int>
    private val cfgTable: MutableMap<RsElement, MutableList<CFGNode>>

    init {
        val size = cfg.graph.nodesCount * wordsPerElement

        this.gens = MutableList(size) { 0 }
        this.actionKills = MutableList(size) { 0 }
        this.scopeKills = MutableList(size) { 0 }
        this.onEntry = MutableList(size) { oper.neutralElement }
        this.cfgTable = cfg.buildLocalIndex()
    }

    private fun getCfgNodes(element: RsElement): List<CFGNode> = cfgTable.getOrDefault(element, mutableListOf())

    private fun hasBitSetForElement(element: RsElement): Boolean = cfgTable.containsKey(element)

    private fun getRange(node: CFGNode): Pair<Int, Int> {
        val start = node.index * wordsPerElement
        val end = start + wordsPerElement
        return Pair(start, end)
    }

    private fun setBit(words: MutableList<Int>, bit: Int): Boolean {
        val word = bit / BITS_PER_INT
        val bitInWord = bit % BITS_PER_INT
        val bitMask = 1 shl bitInWord
        val oldValue = words[word]
        val newValue = oldValue or bitMask
        words[word] = newValue
        return (oldValue != newValue)
    }

    fun addGen(element: RsElement, bit: Int) {
        getCfgNodes(element).forEach {
            val (start, end) = getRange(it)
            setBit(gens.subList(start, end), bit)
        }
    }

    fun addKill(kind: KillFrom, element: RsElement, bit: Int) {
        getCfgNodes(element).forEach {
            val (start, end) = getRange(it)
            when (kind) {
                KillFrom.ScopeEnd -> setBit(scopeKills.subList(start, end), bit)
                KillFrom.Execution -> setBit(actionKills.subList(start, end), bit)
            }
        }
    }

    fun applyGenKill(node: CFGNode, bits: List<Int>): MutableList<Int> {
        val (start, end) = getRange(node)
        val result = bits.toMutableList()
        Union.bitwise(result, gens.subList(start, end))
        Subtract.bitwise(result, actionKills.subList(start, end))
        Subtract.bitwise(result, scopeKills.subList(start, end))
        return result
    }

    fun eachBitOnEntry(element: RsElement, predicate: (Int) -> Boolean): Boolean {
        if (!hasBitSetForElement(element)) return true
        val nodes = getCfgNodes(element)
        return nodes.all { eachBitForNode(EntryOrExit.Entry, it, predicate) }
    }

    @Suppress("SameParameterValue")
    private fun eachBitForNode(e: EntryOrExit, node: CFGNode, predicate: (Int) -> Boolean): Boolean {
        if (bitsPerElement == 0) return true

        val (start, end) = getRange(node)
        val onEntry = onEntry.subList(start, end)
        val slice = when (e) {
            EntryOrExit.Entry -> onEntry
            EntryOrExit.Exit -> applyGenKill(node, onEntry)
        }
        return eachBit(slice, predicate)
    }

    @Suppress("unused")
    fun eachGenBit(element: RsElement, predicate: (Int) -> Boolean): Boolean {
        if (!hasBitSetForElement(element)) return true
        if (bitsPerElement == 0) return true

        val nodes = getCfgNodes(element)
        return nodes.all {
            val (start, end) = getRange(it)
            eachBit(gens.subList(start, end), predicate)
        }
    }

    private fun eachBit(words: List<Int>, predicate: (Int) -> Boolean): Boolean {
        for ((index, word) in words.withIndex()) {
            if (word == 0) continue
            val baseIndex = index * BITS_PER_INT
            for (offset in 0 until BITS_PER_INT) {
                val bit = 1 shl offset
                if (word and bit != 0) {
                    val bitIndex = baseIndex + offset
                    if (bitIndex >= bitsPerElement) {
                        return true
                    } else if (!predicate(bitIndex)) {
                        return false
                    }
                }
            }
        }
        return true
    }

    fun addKillsFromFlowExits() {
        if (bitsPerElement == 0) return

        cfg.graph.forEachEdge { edge ->
            val flowExit = edge.source
            val (start, end) = getRange(flowExit)
            val originalKills = scopeKills.subList(start, end)

            var changed = false
            for (element in edge.data.exitingScopes) {
                val cfgNodes = cfgTable[element] ?: continue
                for (node in cfgNodes) {
                    val (nodeStart, nodeEnd) = getRange(node)
                    val kills = scopeKills.subList(nodeStart, nodeEnd)
                    if (Union.bitwise(originalKills, kills)) {
                        changed = true
                    }
                }
            }
            if (changed) {
                Collections.copy(scopeKills.subList(start, end), originalKills)
            }
        }
    }

    fun propagate() {
        if (bitsPerElement == 0) return

        val propagationContext = PropagationContext(this, true, flowDirection)
        val orderedNodes = when (flowDirection) {
            Forward -> cfg.graph.nodesInPostOrder(cfg.entry).asReversed() // walking in reverse post-order
            Backward -> cfg.graph.nodesInPostOrder(cfg.entry) // walking in post-order
        }
        while (propagationContext.changed) {
            propagationContext.changed = false
            propagationContext.walkCfg(orderedNodes)
        }
    }

    companion object {
        private const val BITS_PER_INT: Int = 32
    }

    private class PropagationContext<O : DataFlowOperator>(
        val dataFlowContext: DataFlowContext<O>,
        var changed: Boolean,
        val flowDirection: FlowDirection
    ) {
        val graph = dataFlowContext.cfg.graph

        fun walkCfg(orderedNodes: List<CFGNode>) {
            for (node in orderedNodes) {
                val (start, end) = dataFlowContext.getRange(node)
                val onEntry = dataFlowContext.onEntry.subList(start, end)
                val result = dataFlowContext.applyGenKill(node, onEntry)
                when (flowDirection) {
                    Forward -> propagateBitsIntoGraphSuccessorsOf(result, node)
                    Backward -> propagateBitsIntoGraphPredecessorsOf(result, node)
                }
            }
        }

        private fun propagateBitsIntoGraphSuccessorsOf(predBits: List<Int>, node: CFGNode) {
            for (edge in graph.outgoingEdges(node)) {
                propagateBitsIntoEntrySetFor(predBits, edge.target)
            }
        }

        private fun propagateBitsIntoGraphPredecessorsOf(predBits: List<Int>, node: CFGNode) {
            for (edge in graph.incomingEdges(node)) {
                propagateBitsIntoEntrySetFor(predBits, edge.source)
            }
        }

        private fun propagateBitsIntoEntrySetFor(predBits: List<Int>, node: CFGNode) {
            val (start, end) = dataFlowContext.getRange(node)
            val onEntry = dataFlowContext.onEntry.subList(start, end)
            val changed = dataFlowContext.oper.bitwise(onEntry, predBits)
            if (changed) {
                this.changed = true
            }
        }
    }
}

interface BitwiseOperator {
    fun join(succ: Int, pred: Int): Int

    fun bitwise(outBits: MutableList<Int>, inBits: List<Int>): Boolean {
        var changed = false

        outBits.zip(inBits).forEachIndexed { i, (outBit, inBit) ->
            val newValue = join(outBit, inBit)
            outBits[i] = newValue
            changed = changed or (outBit != newValue)
        }

        return changed
    }
}

object Union : BitwiseOperator {
    override fun join(succ: Int, pred: Int) = succ or pred
}

object Subtract : BitwiseOperator {
    override fun join(succ: Int, pred: Int) = succ and pred.inv()
}

interface DataFlowOperator : BitwiseOperator {
    val initialValue: Boolean
    val neutralElement: Int get() = if (initialValue) Int.MAX_VALUE else 0
}

enum class KillFrom {
    ScopeEnd, // e.g. a kill associated with the end of the scope of a variable declaration `let x;`
    Execution // e.g. a kill associated with an assignment statement `x = expr;`
}
