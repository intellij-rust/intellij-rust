/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import org.rust.stdext.nextOrNull
import java.util.*

open class Graph<N, E>(
    private val nodes: MutableList<Node<N, E>> = mutableListOf(),
    private val edges: MutableList<Edge<N, E>> = mutableListOf()
) {
    private val nextNodeIndex: Int get() = nodes.size
    private val nextEdgeIndex: Int get() = edges.size

    val nodesCount: Int get() = nodes.size

    fun getNode(index: Int): Node<N, E> =
        nodes[index]

    fun addNode(data: N): Node<N, E> {
        val newNode = Node<N, E>(data, nextNodeIndex)
        nodes.add(newNode)

        return newNode
    }

    fun addEdge(source: Node<N, E>, target: Node<N, E>, data: E): Edge<N, E> {
        val sourceFirst = source.firstOutEdge
        val targetFirst = target.firstInEdge

        val newEdge = Edge(source, target, data, nextEdgeIndex, sourceFirst, targetFirst)
        edges.add(newEdge)

        source.firstOutEdge = newEdge
        target.firstInEdge = newEdge

        return newEdge
    }

    fun addEdge(sourceIndex: Int, targetIndex: Int, data: E): Edge<N, E> {
        val source = nodes[sourceIndex]
        val target = nodes[targetIndex]
        return addEdge(source, target, data)
    }

    fun outgoingEdges(source: Node<N, E>): Sequence<Edge<N, E>> =
        generateSequence(source.firstOutEdge) {
            it.nextSourceEdge
        }

    fun incomingEdges(target: Node<N, E>): Sequence<Edge<N, E>> =
        generateSequence(target.firstInEdge) {
            it.nextTargetEdge
        }

    fun incidentEdges(node: Node<N, E>, direction: Direction): Sequence<Edge<N, E>> =
        when (direction) {
            Direction.OUTGOING -> outgoingEdges(node)
            Direction.INCOMING -> incomingEdges(node)
        }

    fun forEachNode(f: (Node<N, E>) -> Unit) =
        nodes.forEach { f(it) }

    fun forEachEdge(f: (Edge<N, E>) -> Unit) =
        edges.forEach { f(it) }

    fun depthFirstTraversal(startNode: Node<N, E>, direction: Direction = Direction.OUTGOING): Sequence<Node<N, E>> {
        val visited = mutableSetOf(startNode)
        val stack = ArrayDeque<Node<N, E>>()
        stack.push(startNode)

        val visit = { node: Node<N, E> -> if (visited.add(node)) stack.push(node) }

        return generateSequence {
            val next = stack.poll()
            if (next != null) {
                incidentEdges(next, direction).forEach { edge ->
                    val incident = edge.incidentNode(direction)
                    visit(incident)
                }
            }
            next
        }
    }

    fun nodesInPostOrder(entryNode: Node<N, E>, direction: Direction = Direction.OUTGOING): List<Node<N, E>> {
        val visited = mutableSetOf<Node<N, E>>()
        val stack = ArrayDeque<Pair<Node<N, E>, Iterator<Edge<N, E>>>>()
        val result = mutableListOf<Node<N, E>>()
        val pushNode = { node: Node<N, E> ->
            if (visited.add(node)) stack.push(Pair(node, incidentEdges(node, direction).iterator()))
        }

        val nodesWithEntry = listOf(entryNode) + nodes
        for (nextNode in nodesWithEntry) {
            pushNode(nextNode)
            while (stack.isNotEmpty()) {
                val (node, iter) = stack.pop()
                val child = iter.nextOrNull()
                if (child != null) {
                    val incident = child.incidentNode(direction)
                    stack.push(Pair(node, iter))
                    pushNode(incident)
                } else {
                    result.add(node)
                }
            }
        }

        return result
    }
}

interface PresentableNodeData {
    val text: String
}

class PresentableGraph<N : PresentableNodeData, E> : Graph<N, E>() {
    /**
     * Creates graph description written in the DOT language.
     * Usage: copy the output into `cfg.dot` file and run `dot -Tpng cfg.dot -o cfg.png`
     */
    @Suppress("unused")
    fun createDotDescription(): String =
        buildString {
            append("digraph {\n")

            forEachEdge { edge ->
                val source = edge.source
                val target = edge.target
                val sourceNode = source.data
                val targetNode = target.data
                val escapedSourceText = sourceNode.text.replace("\"", "\\\"")
                val escapedTargetText = targetNode.text.replace("\"", "\\\"")

                append("    \"${source.index}: $escapedSourceText\" -> \"${target.index}: $escapedTargetText\";\n")
            }

            append("}\n")
        }

    fun depthFirstTraversalTrace(startNode: Node<N, E> = getNode(0)): String =
        depthFirstTraversal(startNode).joinToString("\n") { it.data.text }
}

class Node<N, E>(
    val data: N,
    val index: Int,
    var firstOutEdge: Edge<N, E>? = null,
    var firstInEdge: Edge<N, E>? = null
)

class Edge<N, E>(
    val source: Node<N, E>,
    val target: Node<N, E>,
    val data: E,
    val index: Int,
    val nextSourceEdge: Edge<N, E>?,
    val nextTargetEdge: Edge<N, E>?
) {
    fun incidentNode(direction: Direction): Node<N, E> =
        when (direction) {
            Direction.OUTGOING -> target
            Direction.INCOMING -> source
        }
}

enum class Direction { OUTGOING, INCOMING }
