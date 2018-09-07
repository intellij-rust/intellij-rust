/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

/**
 * A collection that allows you to take a snapshot ([startSnapshot]) and then roll back collection to snapshot state.
 */
abstract class SnapshotCollection<E> : Iterable<E> {
    protected val undoLog: UndoLog = UndoLog()
    abstract val size: Int

    fun isEmpty(): Boolean = size == 0
    fun inSnapshot(): Boolean = undoLog.inSnapshot()
    fun startSnapshot(): Snapshot = undoLog.startSnapshot()
}

data class SnapshotMap<K, V>(
    private val inner: MutableMap<K, V> = hashMapOf()
) : SnapshotCollection<Map.Entry<K, V>>() {
    override val size: Int get() = inner.size

    override fun iterator(): Iterator<Map.Entry<K, V>> = inner.iterator()

    fun put(key: K, value: V): V? {
        val oldValue = inner.put(key, value)
        undoLog.logChange(if (oldValue == null) Insert(key) else Overwrite(key, oldValue))
        return oldValue
    }

    fun get(key: K): V? = inner[key]

    fun contains(key: K): Boolean = get(key) != null

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

data class SnapshotList<E>(
    val inner: MutableList<E> = mutableListOf()  // TODO: make it private
) : SnapshotCollection<E>() {
    override val size: Int get() = inner.size

    override fun iterator(): Iterator<E> = inner.iterator()

    fun add(element: E) {
        inner.add(element)
        undoLog.logChange(Insert())
    }

    private inner class Insert : Undoable {
        override fun undo() {
            inner.dropLast(1)
        }
    }
}

data class SnapshotSet<E>(
    private val inner: MutableSet<E> = hashSetOf()
) : SnapshotCollection<E>(), Iterable<E> {
    override val size: Int get() = inner.size

    override fun iterator(): Iterator<E> = inner.iterator()

    fun add(element: E): Boolean {
        val success = inner.add(element)
        if (success) undoLog.logChange(Insert(element))
        return success
    }

    private inner class Insert(val element: E) : Undoable {
        override fun undo() {
            inner.remove(element)
        }
    }
}
