/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2.util

import gnu.trove.THash
import gnu.trove.THashMap
import gnu.trove.TObjectHash
import java.util.*

/**
 * Copy of [THashMap], abstracted over values array.
 * Deletion is not supported (we don't need it).
 */
@Suppress("UNCHECKED_CAST")
abstract class THashMapBase<K : Any, V : Any> : TObjectHash<K>(), MutableMap<K, V> {

    abstract fun getValueAtIndex(index: Int): V?
    abstract fun setValueAtIndex(index: Int, value: V)

    override val size: Int
        get() = _size

    override fun put(key: K, value: V): V? {
        var previous: V? = null
        var index = insertionIndex(key)
        val alreadyStored = index < 0
        if (alreadyStored) {
            index = -index - 1
            previous = getValueAtIndex(index)
        }
        _set[index] = key
        setValueAtIndex(index, value)
        if (!alreadyStored) {
            postInsertHook()
        }
        return previous
    }

    override fun putIfAbsent(key: K, value: V): V? {
        val index = insertionIndex(key)
        val alreadyStored = index < 0
        if (alreadyStored) return getValueAtIndex(-index - 1)

        _set[index] = key
        setValueAtIndex(index, value)
        postInsertHook()
        return null
    }

    /** Key deletion is not supported, that's why we `usedFreeSlot` is always true */
    protected fun postInsertHook() = postInsertHook(true)

    protected abstract fun createNewArrays(capacity: Int)

    override fun setUp(initialCapacity: Int): Int {
        val capacity = super.setUp(initialCapacity)
        if (initialCapacity != THash.JUST_CREATED_CAPACITY) {
            createNewArrays(capacity)
        }
        return capacity
    }

    protected fun rehashTemplate(newCapacity: Int, moveValue: (Int, Int) -> Unit) {
        val oldCapacity = _set.size
        val oldKeys = _set
        _set = arrayOfNulls(newCapacity)
        createNewArrays(newCapacity)
        var i = oldCapacity
        while (i-- > 0) {
            if (oldKeys[i] != null) {
                val oldKey = oldKeys[i] as K
                val index = insertionIndex(oldKey)
                if (index < 0) {
                    throwObjectContractViolation(_set[-index - 1], oldKey)
                }
                _set[index] = oldKey
                moveValue(index, i)
            }
        }
    }

    override operator fun get(key: K): V? {
        val index = index(key)
        return if (index < 0) null else getValueAtIndex(index)
    }

    /** Unlike default implementation, doesn't call [containsKey] */
    override fun getOrDefault(key: K, defaultValue: V): V = get(key) ?: defaultValue

    override fun clear() = throw UnsupportedOperationException()

    override fun remove(key: K): V = throw UnsupportedOperationException()

    override fun removeAt(index: Int) = throw UnsupportedOperationException()

    override val values: MutableCollection<V>
        get() = ValueView()

    override val keys: MutableSet<K>
        get() = KeyView()

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = EntryView()

    override fun containsValue(value: V): Boolean = throw UnsupportedOperationException()

    override fun containsKey(key: K): Boolean = contains(key)

    override fun putAll(from: Map<out K, V>) {
        ensureCapacity(from.size)
        for ((key, value) in from) {
            put(key, value)
        }
    }

    private inner class KeyView : AbstractSet<K>() {
        override fun iterator(): MutableIterator<K> {
            return object : THashIterator<K>() {
                override fun objectAtIndex(index: Int): K = _set[index] as K
            }
        }

        override val size: Int get() = _size

        override fun contains(element: K): Boolean = this@THashMapBase.contains(element)
    }

    private inner class ValueView : AbstractCollection<V>() {
        override fun iterator(): MutableIterator<V> {
            return object : THashIterator<V>() {
                override fun objectAtIndex(index: Int): V = getValueAtIndex(index)!!
            }
        }

        override val size: Int get() = _size

        override fun contains(element: V): Boolean = throw UnsupportedOperationException()
    }

    private inner class EntryView : AbstractSet<MutableMap.MutableEntry<K, V>>() {
        override fun iterator(): MutableIterator<Entry<K, V>> {
            return object : THashIterator<Entry<K, V>>() {
                override fun objectAtIndex(index: Int): Entry<K, V> {
                    return Entry(_set[index] as K, getValueAtIndex(index)!!)
                }
            }
        }

        override val size: Int get() = _size

        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean = throw UnsupportedOperationException()
    }

    private class Entry<K : Any, V : Any>(override val key: K, override val value: V) : MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V = throw UnsupportedOperationException()
    }

    private abstract inner class THashIterator<V> : MutableIterator<V> {

        private var index: Int = capacity()

        /**
         * Returns the object at the specified index.
         * Subclasses should implement this to return the appropriate object for the given index.
         */
        protected abstract fun objectAtIndex(index: Int): V

        override operator fun hasNext(): Boolean = nextIndex() >= 0

        override fun next(): V {
            index = nextIndex()
            if (index < 0) throw NoSuchElementException()
            return objectAtIndex(index)
        }

        /**
         * Returns the index of the next value in the data structure
         * or a negative value if the iterator is exhausted.
         */
        private fun nextIndex(): Int {
            val set = _set
            var i = index
            @Suppress("ControlFlowWithEmptyBody")
            while (i-- > 0 && set[i] == null);
            return i
        }

        override fun remove() = throw UnsupportedOperationException()
    }
}
