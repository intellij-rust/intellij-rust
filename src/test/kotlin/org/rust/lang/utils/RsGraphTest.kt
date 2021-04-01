/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import org.rust.RsTestBase

class RsGraphTest : RsTestBase() {
    fun `test graph depth traversal 1`() {
        val graph = simpleGraph1()
        val depthFirst = graph.depthFirstTraversal(graph.getNode(0)).map { it.data }.joinToString(" ")
        assertEquals("1 2 3", depthFirst)
    }

    fun `test graph depth traversal 2`() {
        val graph = simpleGraph2()
        val depthFirst = graph.depthFirstTraversal(graph.getNode(0)).map { it.data }.joinToString(" ")
        assertEquals("1 2 5 6 4", depthFirst)
    }

    fun `test graph postorder 1`() {
        val graph = simpleGraph1()
        val postorder = graph.nodesInPostOrder(graph.getNode(0)).map { it.data }.joinToString(" ")
        assertEquals("3 2 1 4", postorder)
    }

    fun `test graph postorder 2`() {
        val graph = simpleGraph2()
        val postorder = graph.nodesInPostOrder(graph.getNode(0)).map { it.data }.joinToString(" ")
        assertEquals("6 5 2 4 1 3", postorder)
    }

    private fun simpleGraph1(): Graph<Int, String> {
        val graph = Graph<Int, String>()
        (1..4).forEach { graph.addNode(it) }

        graph.addEdge(0, 1, "a")
        graph.addEdge(0, 2, "b")
        graph.addEdge(1, 2, "c")
        graph.addEdge(3, 1, "d")

        return graph
    }

    private fun simpleGraph2(): Graph<Int, String> {
        val graph = Graph<Int, String>()
        (1..6).forEach { graph.addNode(it) }

        graph.addEdge(0, 1, "a")
        graph.addEdge(0, 3, "b")
        graph.addEdge(1, 4, "c")
        graph.addEdge(3, 1, "d")
        graph.addEdge(4, 3, "e")
        graph.addEdge(2, 4, "f")
        graph.addEdge(2, 5, "g")
        graph.addEdge(4, 5, "h")

        return graph
    }
}
