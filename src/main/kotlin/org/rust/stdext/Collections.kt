/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

@Suppress("UNCHECKED_CAST")
fun <T> buildList(builder: (CollectionBuilder<T>).() -> Unit): List<T> =
    buildCollection(mutableListOf(), builder) as List<T>

@Suppress("UNCHECKED_CAST")
fun <T> buildSet(builder: (CollectionBuilder<T>).() -> Unit): Set<T> =
    buildCollection(mutableSetOf(), builder) as Set<T>

private fun <T> buildCollection(result: MutableCollection<T>,
                                builder: (CollectionBuilder<T>).() -> Unit): MutableCollection<T> {
    object : CollectionBuilder<T> {
        override fun add(item: T) {
            result.add(item)
        }

        override fun addAll(items: Collection<T>) {
            result.addAll(items)
        }
    }.builder()
    return result
}

interface CollectionBuilder<in T> {
    fun add(item: T)
    fun addAll(items: Collection<T>)
}

fun makeBitMask(bitToSet: Int): Int = 1 shl bitToSet

fun <K, V1, V2> zipValues(map1: Map<K, V1>, map2: Map<K, V2>): List<Pair<V1, V2>> =
    map1.mapNotNull { (k, v1) -> map2[k]?.let { v2 -> Pair(v1, v2) } }

inline fun <T> List<T>.singleOrFilter(predicate: (T) -> Boolean): List<T> = when {
    size < 2 -> this
    else -> filter(predicate)
}

inline fun <T> List<T>.singleOrLet(function: (List<T>) -> List<T>): List<T> = when {
    size < 2 -> this
    else -> function(this)
}

fun <T> List<T>.chain(other: List<T>): Sequence<T> =
    when {
        other.isEmpty() -> this.asSequence()
        this.isEmpty() -> other.asSequence()
        else -> this.asSequence() + other.asSequence()
    }
