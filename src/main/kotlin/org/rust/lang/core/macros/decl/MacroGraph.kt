/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.decl

import com.intellij.lang.ASTNode
import org.rust.lang.core.macros.decl.MGNodeData.*
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.utils.Node
import org.rust.lang.utils.PresentableGraph
import org.rust.lang.utils.PresentableNodeData
import java.util.*

sealed class MGNodeData : PresentableNodeData {
    class Literal(val value: ASTNode) : MGNodeData() {
        override val text: String get() = value.text
    }

    class Fragment(val kind: FragmentKind) : MGNodeData() {
        override val text: String get() = kind.toString()
    }

    object Start : MGNodeData() {
        override val text: String get() = "START"
    }

    object End : MGNodeData() {
        override val text: String get() = "END"
    }

    object BranchStart : MGNodeData() {
        override val text: String get() = "[S]"
    }

    object BranchEnd : MGNodeData() {
        override val text: String get() = "[E]"
    }
}

typealias MacroGraph = PresentableGraph<MGNodeData, Unit>
typealias MacroGraphNode = Node<MGNodeData, Unit>

class MacroGraphBuilder(private val macro: RsMacro) {
    private val graph = MacroGraph()
    private val preds: Deque<MacroGraphNode> = ArrayDeque<MacroGraphNode>()
    private val pred: MacroGraphNode get() = preds.peek()
    private var result: MacroGraphNode? = null

    fun build(): MacroGraph? {
        val matcher = Matcher.buildFor(macro) ?: return null
        val start = addNode(Start)
        val exit = process(matcher, start)
        addNode(End, exit)
        return graph
    }

    private fun process(matcher: Matcher, pred: MacroGraphNode): MacroGraphNode {
        result = null
        val oldPredsSize = preds.size
        preds.push(pred)
        processMatcher(matcher)
        preds.pop()
        assert(preds.size == oldPredsSize)

        return checkNotNull(result) { "Processing ended inconclusively" }
    }

    private fun addNode(nodeData: MGNodeData, vararg preds: MacroGraphNode): MacroGraphNode {
        val newNode = graph.addNode(nodeData)
        preds.forEach { addEdge(it, newNode) }
        return newNode
    }

    private fun addEdge(source: MacroGraphNode, target: MacroGraphNode) {
        graph.addEdge(source, target, Unit)
    }

    private inline fun finishWith(callable: () -> MacroGraphNode) {
        result = callable()
    }

    private fun finishWith(value: MacroGraphNode) {
        result = value
    }

    private fun processMatcher(matcher: Matcher) {
        return when (matcher) {
            is Matcher.Fragment -> finishWith { addNode(Fragment(matcher.kind), pred) }

            is Matcher.Literal -> finishWith { addNode(Literal(matcher.value), pred) }

            is Matcher.Sequence -> {
                val subMatchersExit = matcher.matchers.fold(pred) { acc, subMatcher -> process(subMatcher, acc) }
                finishWith(subMatchersExit)
            }

            is Matcher.Choice -> {
                val branchStart = addNode(BranchStart, pred)
                val variantsExits = matcher.matchers.map { process(it, branchStart) }.toTypedArray()
                val branchEnd = addNode(BranchEnd, *variantsExits)
                finishWith(branchEnd)
            }

            is Matcher.Optional -> {
                val branchStart = addNode(BranchStart, pred)
                val optMatcherExit = process(matcher.matcher, branchStart)
                val branchEnd = addNode(BranchEnd, optMatcherExit)
                addEdge(branchStart, branchEnd)
                finishWith(branchEnd)
            }

            is Matcher.Repeat -> {
                val branchEnd = addNode(BranchEnd, pred)
                val subMatchersExit = matcher.matchers.fold(branchEnd) { acc, subMatcher -> process(subMatcher, acc) }
                val branchStart = addNode(BranchStart, subMatchersExit)
                val separator = matcher.separator
                if (separator != null) {
                    val separatorNode = addNode(Literal(separator), branchStart)
                    addEdge(separatorNode, branchEnd)
                } else {
                    addEdge(branchStart, branchEnd)
                }
                finishWith(branchStart)
            }
        }
    }
}
