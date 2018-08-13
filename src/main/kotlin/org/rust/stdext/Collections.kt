/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

import java.util.*

@Suppress("UNCHECKED_CAST")
inline fun <T> buildList(builder: (CollectionBuilder<T>).() -> Unit): List<T> =
    buildCollection(mutableListOf(), builder) as List<T>

@Suppress("UNCHECKED_CAST")
inline fun <T> buildSet(builder: (CollectionBuilder<T>).() -> Unit): Set<T> =
    buildCollection(mutableSetOf(), builder) as Set<T>

inline fun <T> buildCollection(
    result: MutableCollection<T>,
    builder: (CollectionBuilder<T>).() -> Unit
): MutableCollection<T> {
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

inline fun <K, V> buildMap(builder: (MapBuilder<K, V>).() -> Unit): Map<K, V> {
    val result = HashMap<K, V>()
    object : MapBuilder<K, V> {
        override fun put(key: K, value: V) {
            result[key] = value
        }

        override fun putAll(map: Map<K, V>) {
            result.putAll(map)
        }
    }.builder()

    return replaceTrivialMap(result)
}

interface MapBuilder<K, in V> {
    fun put(key: K, value: V)
    fun putAll(map: Map<K, V>)
}

fun <K, V> replaceTrivialMap(map: Map<K, V>): Map<K, V> = when (map.size) {
    0 -> emptyMap()
    1 -> {
        val entry = map.entries.single()
        Collections.singletonMap(entry.key, entry.value)
    }
    else -> map
}

private const val INT_MAX_POWER_OF_TWO: Int = Int.MAX_VALUE / 2 + 1

/* Copied from Kotlin's internal Maps.kt */
fun mapCapacity(expectedSize: Int): Int {
    if (expectedSize < 3) {
        return expectedSize + 1
    }
    if (expectedSize < INT_MAX_POWER_OF_TWO) {
        return expectedSize + expectedSize / 3
    }
    return Int.MAX_VALUE // any large value
}

/* Copied from Kotlin's internal Iterables.kt */
fun <T> Iterable<T>.collectionSizeOrDefault(default: Int): Int =
    if (this is Collection<*>) size else default

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

inline fun <T> List<T>.notEmptyOrLet(function: (List<T>) -> List<T>): List<T> = when {
    isNotEmpty() -> this
    else -> function(this)
}

fun <T> List<T>.chain(other: List<T>): Sequence<T> =
    when {
        other.isEmpty() -> this.asSequence()
        this.isEmpty() -> other.asSequence()
        else -> this.asSequence() + other.asSequence()
    }

inline fun <T, R> Iterable<T>.mapToMutableList(transform: (T) -> R): MutableList<R> =
    mapTo(ArrayList(collectionSizeOrDefault(10)), transform)

inline fun <T, R> Iterable<T>.mapToSet(transform: (T) -> R): Set<R> =
    mapTo(HashSet(mapCapacity(collectionSizeOrDefault(10))), transform)

inline fun <T, R: Any> Iterable<T>.mapNotNullToSet(transform: (T) -> R?): Set<R> =
    mapNotNullTo(HashSet(mapCapacity(collectionSizeOrDefault(10))), transform)

inline fun <T> Iterable<T>.joinToWithBuffer(
    buffer: StringBuilder,
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    action: T.(StringBuilder) -> Unit
) {
    buffer.append(prefix)
    var needInsertSeparator = false
    for (element in this) {
        if (needInsertSeparator) {
            buffer.append(separator)
        }
        element.action(buffer)
        needInsertSeparator = true
    }
    buffer.append(postfix)
}

fun <T : Any> Iterator<T>.nextOrNull(): T? =
    if (hasNext()) next() else null

fun <T> MutableList<T>.removeLast(): T = removeAt(size - 1)
