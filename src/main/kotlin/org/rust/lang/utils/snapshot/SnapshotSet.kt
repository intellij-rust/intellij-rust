/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.snapshot

class SnapshotSet<E> private constructor(
    private val inner: MutableSet<E>
) : Snapshotable(), Collection<E> by inner {

    constructor() : this(hashSetOf())

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
