/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

fun createEventBasedVfsBatch(): EventBasedVfsBatch =
    EventBasedVfsBatch183()

private class EventBasedVfsBatch183 : EventBasedVfsBatch() {
    override fun DirCreateEvent.toVFileEvent(): VFileEvent? {
        val vParent = LocalFileSystem.getInstance().findFileByPath(parent.toString()) ?: return null
        return VFileCreateEvent(null, vParent, name, true, true)
    }

    override fun Event.toVFileEvent(): VFileEvent? = when (this) {
        is Event.Create -> {
            val vParent = LocalFileSystem.getInstance().findFileByPath(parent.toString())!!
            VFileCreateEvent(null, vParent, name, false, true)
        }
        is Event.Write -> {
            val vFile = LocalFileSystem.getInstance().findFileByPath(file.toString())!!
            VFileContentChangeEvent(null, vFile, vFile.modificationStamp, -1, true)
        }
        is Event.Delete -> {
            val vFile = LocalFileSystem.getInstance().findFileByPath(file.toString())!!
            VFileDeleteEvent(null, vFile, true)
        }
    }
}
