/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.stdext.randomLowercaseAlphabetic
import java.util.*

interface MacroExpansionVfsBatch {
    interface Path {
        fun toVirtualFile(): VirtualFile?
    }

    fun resolve(file: VirtualFile): Path

    fun createFileWithContent(content: String, stepNumber: Int): Path
    fun deleteFile(file: Path)
    fun writeFile(file: Path, content: String)

    fun applyToVfs()
}

class MacroExpansionVfsBatchImpl(rootDirName: String) : MacroExpansionVfsBatch {
    private val contentRoot = "/$MACRO_EXPANSION_VFS_ROOT/$rootDirName"
    private val batch: VfsBatch = EventBasedVfsBatch()

    override fun resolve(file: VirtualFile): MacroExpansionVfsBatch.Path =
        PathImpl.VFile(file)

    override fun createFileWithContent(content: String, stepNumber: Int): MacroExpansionVfsBatch.Path =
        PathImpl.StringPath(createFileInternal(content, stepNumber))

    private fun createFileInternal(content: String, stepNumber: Int): String {
        val name = randomLowercaseAlphabetic(16)
        return batch.run {
            contentRoot
                .getOrCreateDirectory(stepNumber.toString())
                .getOrCreateDirectory(name[0].toString())
                .getOrCreateDirectory(name[1].toString())
                .createFile(name.substring(2) + ".rs", content)
        }
    }

    override fun deleteFile(file: MacroExpansionVfsBatch.Path) {
        batch.deleteFile((file as PathImpl).toPath())
    }

    override fun writeFile(file: MacroExpansionVfsBatch.Path, content: String) {
        batch.writeFile((file as PathImpl).toPath(), content)
    }

    override fun applyToVfs() {
        batch.applyToVfs()
    }

    private sealed class PathImpl : MacroExpansionVfsBatch.Path {
        abstract fun toPath(): String

        class VFile(val file: VirtualFile): PathImpl() {
            override fun toVirtualFile(): VirtualFile? = file

            override fun toPath(): String = file.path
        }
        class StringPath(val path: String): PathImpl() {
            override fun toVirtualFile(): VirtualFile? =
                MacroExpansionFileSystem.getInstance().findFileByPath(path)

            override fun toPath(): String = path
        }
    }
}

abstract class VfsBatch {
    protected val dirEvents: MutableList<DirCreateEvent> = LinkedList()
    protected val fileEvents: MutableList<Event> = mutableListOf()

    fun String.createFile(name: String, content: String): String =
        createFile(this, name, content)

    @JvmName("createFile_")
    private fun createFile(parent: String, name: String, content: String): String {
        val child = "$parent/$name"
        MacroExpansionFileSystem.getInstance().createFileWithContent(child, content)
        fileEvents.add(Event.Create(parent, name))
        return child
    }

    private fun createDirectory(parent: String, name: String): String {
        val child = "$parent/$name"
        MacroExpansionFileSystem.getInstance().createDirectory(child)
        dirEvents.add(DirCreateEvent(parent, name))
        return child
    }

    fun String.getOrCreateDirectory(name: String): String =
        getOrCreateDirectory(this, name)

    @JvmName("getOrCreateDirectory_")
    private fun getOrCreateDirectory(parent: String, name: String): String {
        val child = "$parent/$name"
        if (MacroExpansionFileSystem.getInstance().exists(child)) {
            return child
        }
        return createDirectory(parent, name)
    }

    fun writeFile(file: String, content: String) {
        MacroExpansionFileSystem.getInstance().setFileContent(file, content)

        fileEvents.add(Event.Write(file))
    }

    fun deleteFile(file: String) {
        MacroExpansionFileSystem.getInstance().deleteFile(file)

        fileEvents.add(Event.Delete(file))
    }

    abstract fun applyToVfs()

    protected class DirCreateEvent(val parent: String, val name: String)

    protected sealed class Event {
        class Create(val parent: String, val name: String): Event()
        class Write(val file: String): Event()
        class Delete(val file: String): Event()
    }

}

class EventBasedVfsBatch : VfsBatch() {
    override fun applyToVfs() {
        checkWriteAccessAllowed()
        if (dirEvents.isEmpty() && fileEvents.isEmpty()) return

        val manager = VirtualFileManager.getInstance() as VirtualFileManagerEx
        manager.fireBeforeRefreshStart(/*asynchronous = */ false)
        try {
            val events = mutableListOf<VFileEvent>()
            while (dirEvents.isNotEmpty()) {
                val iter = dirEvents.iterator()
                while (iter.hasNext()) {
                    val event = iter.next().toVFileEvent()
                    if (event != null) {
                        iter.remove()
                        events.add(event)
                    }
                }
                check(events.isNotEmpty())
                PersistentFS.getInstance().processEvents(events)
                events.clear()
            }

            if (fileEvents.isNotEmpty()) {
                PersistentFS.getInstance().processEvents(fileEvents.mapNotNull { it.toVFileEvent() })
                fileEvents.clear()
            }
        } finally {
            manager.fireAfterRefreshFinish(/*asynchronous = */ false)
        }
    }

    private fun DirCreateEvent.toVFileEvent(): VFileEvent? {
        val vParent = MacroExpansionFileSystem.getInstance().findFileByPath(parent) ?: return null
        @Suppress("UnstableApiUsage")
        return VFileCreateEvent(null, vParent, name, true, null, null, true, ChildInfo.EMPTY_ARRAY)
    }

    private fun Event.toVFileEvent(): VFileEvent? = when (this) {
        is Event.Create -> {
            val vParent = MacroExpansionFileSystem.getInstance().findFileByPath(parent)!!
            val attributes = FileSystemUtil.getAttributes("$parent/$name")
            VFileCreateEvent(null, vParent, name, false, attributes, null, true, null)
        }
        is Event.Write -> {
            val vFile = MacroExpansionFileSystem.getInstance().findFileByPath(file)!!
            VFileContentChangeEvent(null, vFile, vFile.modificationStamp, -1, true)
        }
        is Event.Delete -> {
            val vFile = MacroExpansionFileSystem.getInstance().findFileByPath(file)
            // skip if file is already deleted (not sure how this can happen)
            if (vFile == null) null else VFileDeleteEvent(null, vFile, true)
        }
    }
}
