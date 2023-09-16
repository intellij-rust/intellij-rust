/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.util

import org.rust.lang.core.mir.WithIndex

class IndexKeyMap<K : WithIndex, V : Any> private constructor(
    private val inner: MutableList<V?>,
) : MutableMap<K, V> {
    constructor() : this(mutableListOf())

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = throw UnsupportedOperationException()

    override val keys: MutableSet<K>
        get() = throw UnsupportedOperationException()

    override val size: Int
        get() = inner.count { it != null }

    override val values: MutableCollection<V>
        get() = throw UnsupportedOperationException()

    override fun clear() {
        for (i in inner.indices) {
            inner[i] = null
        }
    }

    override fun isEmpty(): Boolean = size == 0

    override fun remove(key: K): V? {
        val oldValue = inner[key.index]
        inner[key.index] = null
        return oldValue
    }

    override fun putAll(from: Map<out K, V>) {
        for ((k, v) in from) {
            put(k, v)
        }
    }

    override fun put(key: K, value: V): V? {
        val index = key.index
        if (inner.size <= index) {
            for (i in inner.size..index) {
                inner.add(null)
            }
        }
        val oldValue = inner[index]
        inner[index] = value
        return oldValue
    }

    override fun get(key: K): V? = inner.getOrNull(key.index)

    override fun containsValue(value: V): Boolean = inner.contains(value)

    override fun containsKey(key: K): Boolean = get(key) != null

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <K : WithIndex, V : Any> fromListUnchecked(
            list: List<V>
        ): Map<K, V> = IndexKeyMap(list as MutableList<V?>)
    }
}
