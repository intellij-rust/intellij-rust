/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils.snapshot

import org.rust.stdext.removeLast

class SnapshotList<E> private constructor(
    private val inner: MutableList<E>
) : Snapshotable(), Collection<E> by inner {

    constructor() : this(mutableListOf())

    fun add(element: E): Boolean {
        val success = inner.add(element)
        if (success) undoLog.logChange(Insert())
        return success
    }

    private inner class Insert : Undoable {
        override fun undo() {
            inner.removeLast()
        }
    }
}
