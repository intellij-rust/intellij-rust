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
        checkGraphTraversal(depthFirst, "1 2 3")
    }

    fun `test graph depth traversal 2`() {
        val graph = simpleGraph2()
        val depthFirst = graph.depthFirstTraversal(graph.getNode(0)).map { it.data }.joinToString(" ")
        checkGraphTraversal(depthFirst, "1 2 5 6 4")
    }

    fun `test graph postorder 1`() {
        val graph = simpleGraph1()
        val postorder = graph.nodesInPostOrder(graph.getNode(0)).map { it.data }.joinToString(" ")
        checkGraphTraversal(postorder, "3 2 1 4")
    }

    fun `test graph postorder 2`() {
        val graph = simpleGraph2()
        val postorder = graph.nodesInPostOrder(graph.getNode(0)).map { it.data }.joinToString(" ")
        checkGraphTraversal(postorder, "6 5 2 4 1 3")
    }

    private fun checkGraphTraversal(actual: String, expected: String) {
        check(actual == expected) { "Expected: $expected, found: $actual" }
    }

    fun simpleGraph1(): Graph<Int, String> {
        val graph = Graph<Int, String>()
        (1..4).forEach { graph.addNode(it) }

        graph.addEdge(0, 1, "a")
        graph.addEdge(0, 2, "b")
        graph.addEdge(1, 2, "c")
        graph.addEdge(3, 1, "d")

        return graph
    }

    fun simpleGraph2(): Graph<Int, String> {
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
