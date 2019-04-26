/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

fun createEventBasedVfsBatch(): EventBasedVfsBatch =
    EventBasedVfsBatch191()

private class EventBasedVfsBatch191 : EventBasedVfsBatch() {
    override fun DirCreateEvent.toVFileEvent(): VFileEvent? {
        val vParent = LocalFileSystem.getInstance().findFileByPath(parent.toString()) ?: return null
        return VFileCreateEvent(null, vParent, name, true, null, null, true, false)
    }

    override fun Event.toVFileEvent(): VFileEvent? = when (this) {
        is Event.Create -> {
            val vParent = LocalFileSystem.getInstance().findFileByPath(parent.toString())!!
            val attributes = FileSystemUtil.getAttributes(parent.resolve(name).toString())
            VFileCreateEvent(null, vParent, name, false, attributes, null, true, false)
        }
        is Event.Write -> {
            val vFile = LocalFileSystem.getInstance().findFileByPath(file.toString())!!
            VFileContentChangeEvent(null, vFile, vFile.modificationStamp, -1, true)
        }
        is Event.Delete -> {
            val vFile = LocalFileSystem.getInstance().findFileByPath(file.toString())
            // skip if file is already deleted (not sure how this can happen)
            if (vFile == null) null else VFileDeleteEvent(null, vFile, true)
        }
    }
}
