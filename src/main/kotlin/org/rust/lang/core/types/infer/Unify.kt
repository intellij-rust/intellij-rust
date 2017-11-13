/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

interface NodeOrValue
interface Node: NodeOrValue {
    var parent: NodeOrValue
}
data class VarValue<out V>(val value: V?, val rank: Int): NodeOrValue

/**
 * [UnificationTable] is map from [K] to [V] with additional ability
 * to redirect certain K's to a single V en-masse with the help of
 * disjoint set union.
 *
 * We implement Tarjan's union-find
 * algorithm: when two keys are unified, one of them is converted
 * into a "redirect" pointing at the other. These redirects form a
 * DAG: the roots of the DAG (nodes that are not redirected) are each
 * associated with a value of type `V` and a rank. The rank is used
 * to keep the DAG relatively balanced, which helps keep the running
 * time of the algorithm under control. For more information, see
 * <http://en.wikipedia.org/wiki/Disjoint-set_data_structure>.
 */
@Suppress("UNCHECKED_CAST")
class UnificationTable<K : Node, V> {
    private val undoLog: MutableList<UndoLogEntry> = mutableListOf()

    @Suppress("UNCHECKED_CAST")
    private data class Root<out K: Node, out V>(val key: K) {
        private val varValue: VarValue<V> = key.parent as VarValue<V>
        val rank: Int get() = varValue.rank
        val value: V? get() = varValue.value
    }

    private fun get(key: Node): Root<K, V> {
        val parent = key.parent
        return if (parent is Node) {
            val root = get(parent)
            if (key.parent != root.key) {
                logNodeState(key)
                key.parent = root.key // Path compression
            }
            root
        } else {
            Root(key as K)
        }
    }

    private fun setValue(root: Root<K, V>, value: V) {
        logNodeState(root.key)
        root.key.parent = VarValue(value, root.rank)
    }

    private fun unify(rootA: Root<K, V>, rootB: Root<K, V>, newValue: V?): K {
        return when {
        // a has greater rank, so a should become b's parent,
        // i.e., b should redirect to a.
            rootA.rank > rootB.rank -> redirectRoot(rootA.rank, rootB, rootA, newValue)
        // b has greater rank, so a should redirect to b.
            rootA.rank < rootB.rank -> redirectRoot(rootB.rank, rootA, rootB, newValue)
        // If equal, redirect one to the other and increment the
        // other's rank.
            else -> redirectRoot(rootA.rank + 1, rootA, rootB, newValue)
        }
    }

    private fun redirectRoot(newRank: Int, oldRoot: Root<K, V>, newRoot: Root<K, V>, newValue: V?): K {
        val oldRootKey = oldRoot.key
        val newRootKey = newRoot.key
        logNodeState(newRootKey)
        logNodeState(oldRootKey)
        oldRootKey.parent = newRootKey
        newRootKey.parent = VarValue(newValue, newRank)
        return newRootKey
    }

    fun findRoot(key: K): K = get(key).key

    fun findValue(key: K): V? = get(key).value

    fun unifyVarVar(key1: K, key2: K): K? {
        val node1 = get(key1)
        val node2 = get(key2)

        if (node1.key == node2.key) return node1.key // already unified

        val val1 = node1.value
        val val2 = node2.value

        val newVal = if (val1 != null && val2 != null) {
            if (val1 != val2) error("unification error") // must be solved on the upper level
            val1
        } else {
            val1 ?: val2
        }

        return unify(node1, node2, newVal)
    }

    fun unifyVarValue(key: K, value: V) {
        val node = get(key)
        if (node.value != null && node.value != value) error("unification error") // must be solved on the upper level

        setValue(node, value)
    }

    private fun logNodeState(node: Node) {
        if (isSnapshot()) undoLog.add(UndoLogEntry.SetParent(node, node.parent))
    }

    private fun isSnapshot(): Boolean = !undoLog.isEmpty()

    fun startSnapshot(): Snapshot {
        undoLog.add(UndoLogEntry.OpenSnapshot)
        return SnapshotImpl(undoLog.size - 1)
    }

    private inner class SnapshotImpl(val position: Int): Snapshot {
        override fun commit() {
            assertOpenSnapshot(this)
            if (position == 0) {
                undoLog.clear()
            } else {
                undoLog[position] = UndoLogEntry.CommittedSnapshot
            }
        }

        override fun rollback(){
            val snapshoted = undoLog.subList(position + 1, undoLog.size)
            snapshoted.reversed().forEach(UndoLogEntry::rollback)
            snapshoted.clear()

            val last = undoLog.removeAt(undoLog.size - 1)
            check(last is UndoLogEntry.OpenSnapshot)
            check(undoLog.size == position)
        }

        private fun assertOpenSnapshot(snapshot: SnapshotImpl) {
            check(undoLog.getOrNull(snapshot.position) is UndoLogEntry.OpenSnapshot)
        }
    }
}

interface Snapshot {
    fun commit()

    fun rollback()
}

private sealed class UndoLogEntry {
    abstract fun rollback()

    object OpenSnapshot : UndoLogEntry() {
        override fun rollback() {
            error("Cannot rollback an uncommitted snapshot")
        }
    }
    object CommittedSnapshot : UndoLogEntry() {
        override fun rollback() {
            // This occurs when there are nested snapshots and
            // the inner is committed but outer is rolled back.
        }
    }
    data class SetParent(val node: Node, val oldParent: NodeOrValue) : UndoLogEntry() {
        override fun rollback() {
            node.parent = oldParent
        }
    }
}
