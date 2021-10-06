/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2.util

import gnu.trove.THash

/** Optimized version of `THashMap<K, SmartList<V>>` */
@Suppress("UNCHECKED_CAST")
class SmartListMap<K : Any, V : Any> : THashMapBase<K, List<V>>() {

    private var items: Array<Any? /* V or List<V> */> = THash.EMPTY_OBJECT_ARRAY

    override val size: Int get() = _size

    override fun getValueAtIndex(index: Int): List<V>? {
        val value = items[index] ?: return null
        return if (value is List<*>) {
            value as List<V>
        } else {
            listOf(value as V)
        }
    }

    override fun setValueAtIndex(index: Int, value: List<V>) {
        items[index] = value.singleOrNull() ?: value
    }

    fun addValue(key: K, value: V) {
        val index = insertionIndex(key)
        val alreadyStored = index < 0
        if (alreadyStored) {
            val indexAdjusted = -index - 1
            val existing = items[indexAdjusted]
            if (existing is MutableList<*>) {
                (existing as MutableList<V>) += value
            } else {
                items[indexAdjusted] = mutableListOf(existing, value)
            }
        } else {
            _set[index] = key
            items[index] = value
            postInsertHook()
        }
    }

    override fun createNewArrays(capacity: Int) {
        items = arrayOfNulls(capacity)
    }

    override fun rehash(newCapacity: Int) {
        val oldItems = items
        rehashTemplate(newCapacity) { newIndex, oldIndex ->
            items[newIndex] = oldItems[oldIndex]
        }
    }
}
