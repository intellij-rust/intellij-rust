/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

/**
 * Works like a regular [MutableMap], but additionally allows you to take a snapshot
 * ([startSnapshot]) and then roll back the map to the snapshot state.
 * Note: this map currently don't support remove/clear operations
 */
class SnapshotMap<K, V> {
    private val inner: MutableMap<K, V> = HashMap()
    private val undoLog: UndoLog = UndoLog()

    fun put(key: K, value: V): V? {
        val oldValue = inner.put(key, value)
        undoLog.logChange(if (oldValue == null) Insert(inner, key) else Overwrite(inner, key, oldValue))
        return oldValue
    }

    fun get(key: K): V? = inner[key]

    fun startSnapshot(): Snapshot = undoLog.startSnapshot()

    private data class Insert<K, V>(val map: MutableMap<K, V>, val key: K) : Undoable {
        override fun undo() {
            map.remove(key)
        }
    }

    private data class Overwrite<K, V>(val map: MutableMap<K, V>, val key: K, val oldValue: V) : Undoable {
        override fun undo() {
            map[key] = oldValue
        }
    }
}
