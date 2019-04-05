/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.util.io.*
import org.apache.commons.lang.RandomStringUtils
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.openapiext.pathAsPath
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

interface MacroExpansionVfsBatch {
    interface Path {
        fun toVirtualFile(): VirtualFile?
    }

    fun resolve(file: VirtualFile): Path

    fun createFileWithContent(content: String): Path
    fun deleteFile(file: Path)
    fun writeFile(file: Path, content: String)

    fun applyToVfs()
}

class LocalFsMacroExpansionVfsBatch(
    private val realFsExpansionContentRoot: Path
) : MacroExpansionVfsBatch {
    private val batch: VfsBatch = createEventBasedVfsBatch()

    override fun resolve(file: VirtualFile): MacroExpansionVfsBatch.Path =
        PathImpl.VFile(file)

    override fun createFileWithContent(content: String): MacroExpansionVfsBatch.Path =
        PathImpl.NioPath(createFileInternal(content))

    private fun createFileInternal(content: String): Path {
        val name = RandomStringUtils.random(16, "0123456789abcdifghijklmnopqrstuvwxyz")
        return batch.run {
            realFsExpansionContentRoot
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
        abstract fun toPath(): Path

        class VFile(val file: VirtualFile): PathImpl() {
            override fun toVirtualFile(): VirtualFile? = file

            override fun toPath(): Path = file.pathAsPath
        }
        class NioPath(val path: Path): PathImpl() {
            override fun toVirtualFile(): VirtualFile? =
                LocalFileSystem.getInstance().findFileByIoFile(path.toFile())

            override fun toPath(): Path = path
        }
    }
}

abstract class VfsBatch {
    protected val dirEvents: MutableList<DirCreateEvent> = LinkedList()
    protected val fileEvents: MutableList<Event> = mutableListOf()

    fun Path.createFile(name: String, content: String): Path =
        createFile(this, name, content)

    @JvmName("createFile_")
    private fun createFile(parent: Path, name: String, content: String): Path {
        val child = parent.resolve(name)
        check(!child.exists())
        child.createFile()
        val data = content.toByteArray() // UTF-8
        Files.write(child, data)

        fileEvents.add(Event.Create(parent, name, child.lastModified().toMillis(), data.size))
        return child
    }

    private fun createDirectory(parent: Path, name: String): Path {
        val child = parent.resolve(name)
        check(!child.exists())
        Files.createDirectory(child)

        dirEvents.add(DirCreateEvent(parent, name))

        return child
    }

    fun Path.getOrCreateDirectory(name: String): Path =
        getOrCreateDirectory(this, name)

    @JvmName("getOrCreateDirectory_")
    private fun getOrCreateDirectory(parent: Path, name: String): Path {
        val child = parent.resolve(name)
        if (child.exists()) {
            return child
        }
        return createDirectory(parent, name)
    }

    fun writeFile(file: Path, content: String) {
        check(file.isFile())
        file.write(content) // UTF-8

        fileEvents.add(Event.Write(file))
    }

    fun deleteFile(file: Path) {
        file.delete()

        fileEvents.add(Event.Delete(file))
    }

    abstract fun applyToVfs()

    protected class DirCreateEvent(val parent: Path, val name: String)

    protected sealed class Event {
        class Create(val parent: Path, val name: String, val lastModified: Long, val length: Int): Event()
        class Write(val file: Path): Event()
        class Delete(val file: Path): Event()
    }

}

abstract class EventBasedVfsBatch : VfsBatch() {
    override fun applyToVfs() {
        checkWriteAccessAllowed()
        if (dirEvents.isEmpty() && fileEvents.isEmpty()) return

        val manager = VirtualFileManager.getInstance() as VirtualFileManagerEx
        manager.fireBeforeRefreshStart(/*asynchronous = */ false)
        try {
            val events = mutableListOf<VFileEvent>()
            while (!dirEvents.isEmpty()) {
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
                PersistentFS.getInstance().processEvents(fileEvents.map { it.toVFileEvent()!! })
                fileEvents.clear()
            }
        } finally {
            manager.fireAfterRefreshFinish(/*asynchronous = */ false)
        }
    }

    protected abstract fun DirCreateEvent.toVFileEvent(): VFileEvent?

    protected abstract fun Event.toVFileEvent(): VFileEvent?
}
