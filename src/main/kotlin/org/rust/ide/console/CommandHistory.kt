/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

class CommandHistory {
    class Entry(val entryText: String)

    private val entries: ArrayList<Entry> = arrayListOf()

    val listeners: ArrayList<HistoryUpdateListener> = arrayListOf()

    operator fun get(i: Int): Entry = entries[i]

    fun addEntry(entry: Entry) {
        entries.add(entry)
        listeners.forEach { it.onNewEntry(entry) }
    }

    val size: Int
        get() = entries.size
}

interface HistoryUpdateListener {
    fun onNewEntry(entry: CommandHistory.Entry)
}
