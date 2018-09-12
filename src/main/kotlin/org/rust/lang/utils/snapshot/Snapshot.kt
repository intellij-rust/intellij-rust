/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.snapshot

interface Snapshot {
    fun commit()
    fun rollback()
}

interface Undoable {
    fun undo()
}

/** An entity that allows you to take a snapshot ([startSnapshot]) and then roll back to snapshot state. */
abstract class Snapshotable {
    protected val undoLog: UndoLog = UndoLog()

    fun inSnapshot(): Boolean = undoLog.inSnapshot()
    fun startSnapshot(): Snapshot = undoLog.startSnapshot()
}

class UndoLog {
    private val undoLog: MutableList<Undoable> = mutableListOf()

    fun inSnapshot(): Boolean = undoLog.isNotEmpty()

    fun logChange(undoable: Undoable) {
        if (inSnapshot()) undoLog.add(undoable)
    }

    fun startSnapshot(): Snapshot = LogBasedSnapshot.start(undoLog)
}

private class LogBasedSnapshot private constructor(
    private val undoLog: MutableList<Undoable>,
    val position: Int
) : Snapshot {
    override fun commit() {
        assertOpenSnapshot()
        if (position == 0) {
            undoLog.clear()
        } else {
            undoLog[position] = CommittedSnapshot
        }
    }

    override fun rollback() {
        assertOpenSnapshot()
        val toRollback = undoLog.subList(position + 1, undoLog.size)
        toRollback.asReversed().forEach(Undoable::undo)
        toRollback.clear()

        val last = undoLog.removeAt(undoLog.size - 1)
        check(last == OpenSnapshot)
        check(undoLog.size == position)
    }

    private fun assertOpenSnapshot() {
        check(undoLog.getOrNull(position) == OpenSnapshot)
    }

    companion object {
        fun start(undoLog: MutableList<Undoable>): Snapshot {
            undoLog.add(OpenSnapshot)
            return LogBasedSnapshot(undoLog, undoLog.size - 1)
        }
    }

    private object OpenSnapshot : Undoable {
        override fun undo() {
            error("Cannot rollback an uncommitted snapshot")
        }
    }

    private object CommittedSnapshot : Undoable {
        override fun undo() {
            // This occurs when there are nested snapshots and
            // the inner is committed but outer is rolled back.
        }
    }
}

class CombinedSnapshot(vararg val snapshots: Snapshot) : Snapshot {
    override fun rollback() = snapshots.forEach { it.rollback() }
    override fun commit() = snapshots.forEach { it.commit() }
}
