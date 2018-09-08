/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.snapshot

class SnapshotMap<K, V> : Snapshotable() {
    private val inner: MutableMap<K, V> = hashMapOf()

    val size: Int get() = inner.size

    fun isEmpty(): Boolean = inner.isEmpty()

    fun iterator(): Iterator<Map.Entry<K, V>> = inner.iterator()

    fun contains(key: K): Boolean = inner.contains(key)

    operator fun get(key: K): V? = inner[key]

    operator fun set(key: K, value: V) {
        put(key, value)
    }

    fun put(key: K, value: V): V? {
        val oldValue = inner.put(key, value)
        undoLog.logChange(if (oldValue == null) Insert(key) else Overwrite(key, oldValue))
        return oldValue
    }

    private inner class Insert(val key: K) : Undoable {
        override fun undo() {
            inner.remove(key)
        }
    }

    private inner class Overwrite(val key: K, val oldValue: V) : Undoable {
        override fun undo() {
            inner[key] = oldValue
        }
    }
}
