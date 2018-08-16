/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer.outlives

import org.rust.stdext.mapToMutableList
import org.rust.stdext.removeLast
import java.util.*
import java.util.stream.Collectors

class TransitiveRelation<T> {
    /** List of elements. This is used to map from a T to an index. */
    private val elements: MutableList<T> = mutableListOf()

    /** Maps each element to an index. */
    private val map: MutableMap<T, Int> = hashMapOf()

    /** List of base edges in the graph. Require to compute transitive closure. */
    private val edges: MutableList<Edge> = mutableListOf()

    /** Cached transitive closure derived from the edges. */
    private var closure: BitMatrix? = null

    fun isEmpty(): Boolean = edges.isEmpty()

    /** Indicate that `[element1] < [element2]` (where `<` is this relation). */
    fun add(element1: T, element2: T) {
        val source = addIndex(element1)
        val target = addIndex(element2)
        val edge = Edge(source, target)
        if (edges.add(edge)) {
            // added an edge, clear the cache
            closure = null
        }
    }

    /** Check whether `[element1] < [element2]` (transitively). */
    fun contains(element1: T, element2: T): Boolean {
        val i = getIndex(element1) ?: return false
        val j = getIndex(element2) ?: return false
        return withClosure { closure -> closure[i, j] }
    }

    private fun getIndex(element: T): Int? = map[element]

    private fun addIndex(element: T): Int =
        map.computeIfAbsent(element) {
            elements.add(element)
            closure = null  // if we changed the dimensions, clear the cache
            elements.size - 1
        }

    /**
     * Picks the "postdominating" upper-bound for `a` and `b`. This is usually the least upper bound, but in cases
     * where there is no single least upper bound, it is the "mutual immediate postdominator".
     */
    fun getPostdomUpperBound(element1: T, element2: T): T? =
        getMutualImmediatePostdominator(getMinimalUpperBounds(element1, element2))

    /** Viewing the relation as a graph, computes the "mutual immediate postdominator" of a set of points. */
    private fun getMutualImmediatePostdominator(minimalUpperBounds: MutableList<T>): T? {
        while (true) {
            when (minimalUpperBounds.size) {
                0 -> return null
                1 -> return minimalUpperBounds.first()
                else -> {
                    val first = minimalUpperBounds.removeLast()
                    val second = minimalUpperBounds.removeLast()
                    minimalUpperBounds.addAll(getMinimalUpperBounds(first, second))
                }
            }
        }
    }

    /**
     * Returns the set of bounds `X` such that:
     * - `a < X` and `b < X`
     * - there is no `Y != X` such that `a < Y` and `Y < X`
     */
    private fun getMinimalUpperBounds(element1: T, element2: T): MutableList<T> {
        var i = getIndex(element1) ?: return mutableListOf()
        var j = getIndex(element2) ?: return mutableListOf()

        if (i > j) {
            val temp = i
            i = j
            j = temp
        }

        val leastUpperBoundIndices = withClosure { closure ->
            // Easy case is when either `a < b` or `b < a`:
            if (closure[i, j]) return@withClosure mutableListOf(j)
            if (closure[j, i]) return@withClosure mutableListOf(i)

            val candidates = closure.intersection(i, j)
            pareDown(candidates, closure)
            candidates.reverse()
            pareDown(candidates, closure)
            candidates
        }

        return leastUpperBoundIndices.reversed().mapToMutableList { elements[it] }
    }

    private fun <R> withClosure(action: (BitMatrix) -> R): R {
        val concreteClosure = closure ?: computeClosure()
        val result = action(concreteClosure)
        closure = concreteClosure
        return result
    }

    private fun computeClosure(): BitMatrix {
        val matrix = BitMatrix(elements.size, elements.size)
        var changed = true
        while (changed) {
            changed = false
            for ((source, target) in edges) {
                // add an edge from S -> T
                changed = changed || matrix.set(source, target)
                // add all outgoing edges from T into S
                changed = changed || matrix.merge(target, source)
            }
        }
        return matrix
    }
}

/**
 * Pare down is used as a step in the LUB computation. It edits the candidates array in place by removing any element
 * `j` for which there exists an earlier element `i < j` such that `i -> j`.
 */
private fun pareDown(candidates: MutableList<Int>, closure: BitMatrix) {
    for (i in 0 until candidates.size) {
        var dead = 0
        for (j in i + 1 until candidates.size) {
            if (closure[candidates[i], candidates[j]]) {
                // If `i` can reach `j`, then we can remove `j`.
                // So just mark it as dead and move on; subsequent indices will be shifted into its place.
                dead++
            } else {
                candidates[j - dead] = candidates[j]
            }
        }
        candidates.subList(candidates.size - dead, candidates.size).clear()
    }
}

/**
 * A "bit matrix" is basically a matrix of booleans represented as one gigantic bitset.
 * In other words, it is as if you have [rows] bitsets, each of length [columns].
 */
class BitMatrix(private val rows: Int, private val columns: Int) {
    private val bits: BitSet = BitSet(rows * columns)

    /**
     * Sets the cell at ([row], [column]) to true.
     * Put another way, add [column] to the bitset for [row].
     * @return true if this changed the matrix, and false otherwise.
     */
    operator fun set(row: Int, column: Int): Boolean {
        require(row < rows && column < columns)
        val oldValue = get(row, column)
        bits.set(getIndex(row, column))
        return !oldValue
    }

    /**
     * Do the bits from [row] contain [column]? Put another way, is the matrix cell at ([row], [column]) true?
     * Put yet another way, if the matrix represents (transitive) reachability, can [row] reach [column]?
     */
    operator fun get(row: Int, column: Int): Boolean {
        require(row < rows && column < columns)
        return bits.get(getIndex(row, column))
    }

    /** @return those indices that are true in [row1] and [row2]. */
    fun intersection(row1: Int, row2: Int): MutableList<Int> {
        require(row1 < rows && row2 < rows)

        val bits1 = bits.getRow(row1)
        val bits2 = bits.getRow(row2)

        bits1.and(bits2)
        return bits1.toMutableList()
    }

    /**
     * Add the bits from [source] row to the bits from [target] row.
     * @return true if anything changed.
     */
    fun merge(source: Int, target: Int): Boolean {
        require(source < rows && target < rows)

        val sourceBits = bits.getRow(source)
        val targetBits = bits.getRow(target)

        targetBits.and(sourceBits)
        targetBits.xor(sourceBits)

        var changed = false
        for (column in targetBits.stream()) {
            changed = changed || set(target, column)
        }
        return changed
    }

    private fun getIndex(row: Int, column: Int = 0): Int = row * columns + column

    private fun BitSet.getRow(row: Int): BitSet = get(getIndex(row), getIndex(row + 1))
}

private data class Edge(val source: Int, val target: Int)

private fun BitSet.toMutableList(): MutableList<Int> =
    stream().mapToObj { it }.collect(Collectors.toList())
