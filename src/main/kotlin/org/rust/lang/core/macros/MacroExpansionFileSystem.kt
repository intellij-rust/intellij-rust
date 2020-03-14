/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.local.LocalFileSystemBase
import com.intellij.util.ArrayUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.PathUtilRt
import org.rust.lang.core.macros.MacroExpansionFileSystem.Companion.readFSItem
import org.rust.lang.core.macros.MacroExpansionFileSystem.Companion.writeFSItem
import org.rust.lang.core.macros.MacroExpansionFileSystem.FSItem
import org.rust.lang.core.macros.MacroExpansionFileSystem.FSItem.FSDir
import org.rust.lang.core.macros.MacroExpansionFileSystem.FSItem.FSFile
import java.io.*

/**
 * An implementation of [com.intellij.openapi.vfs.VirtualFileSystem] used to store macro expansions.
 *
 * The problem is that we need to store tens of thousands of small files (with macro expansions).
 * A real filesystem is slow, and a lot small files consumes too much of diskspace.
 * [com.intellij.openapi.vfs.ex.temp.TempFileSystem] can't be used because these files should
 * persist between IDE restarts, and because `TempFileSystem` stores all files in the RAM.
 *
 * [MacroExpansionFileSystem] is a "snapshot-only VFS", i.e. it doesn't have any "backend" (like
 * a real FS in [com.intellij.openapi.vfs.LocalFileSystem] or RAM in
 * [com.intellij.openapi.vfs.ex.temp.TempFileSystem]) and file contents are stored only in the
 * snapshot ([com.intellij.openapi.vfs.newvfs.persistent.PersistentFS]).
 *
 * ## Under the hood
 * The platform VFS implementation ([com.intellij.openapi.vfs.newvfs.persistent.PersistentFS])
 * sometimes (during [refresh]) invokes methods [getTimeStamp] and [getLength] from a backend
 * filesystem (i.e. [MacroExpansionFileSystem]) to check that files does not change (and reloads
 * changed files). To satisfy this mechanism, [MacroExpansionFileSystem] maintain file tree
 * structure with `last modified` and `length` properties, but without file contents (to be honest,
 * there is a "temporary" file content [FSItem.FSFile.tempContent] that is cleared once loaded
 * to the snapshot). [MacroExpansionFileSystem] stores such tree in-memory, but since it should
 * persist between the IDE restarts, there are methods that allow loading/storing parts of the file
 * tree externally (see [getDirectory], [setDirectory], [readFSItem], [writeFSItem])
 *
 * Since VFS is global and exists regardless of open projects, [MacroExpansionFileSystem] should
 * either store the file tree in memory for all ever existing projects, or load subtrees on-demand
 * for opened projects. Storing the file tree in memory for all ever existing projects can be too
 * expensive, so on-demand solution is preferred.
 *
 * For macro expansions we use such directory layout:
 * "/rust_expanded_macros/<random_project_id>/a/b/<random_file_name>.rs", where "<random_project_id>" is a
 * project root that should be loaded/unloaded on-demand. Loading is trivial - it's just a combination of
 * [readFSItem] and [setDirectory] methods. More interesting is unloading. There is a problem with it:
 * [MacroExpansionFileSystem] still should say `last modified` and `length` to the platform for
 * _all_ files, so we can't just remove a project subtree. But with a knowledge of how [refresh]
 * works we made a trick.
 *
 * Quick explanation of the [refresh] process works. First, a file or directory should be marked as dirty
 * with [com.intellij.openapi.vfs.newvfs.NewVirtualFile.markDirty]. This method also marks dirty all
 * ancestor directories of the file, i.e. if we mark a file "/foo/bar/baz.rs" as dirty, "bar", "foo"
 * and "/" (root) directories will be also marked dirty. Then [refresh] machinery, starting from the root
 * directory, traverses dirty or modified ([getTimeStamp]) directories.
 *
 * So, if we want to trick the refresher, [MacroExpansionFileSystem] should always store in-memory
 * list of "/rust_expanded_macros/<random_project_id>" directories with their `last modified` values
 * and answer "yes, this directory still exists and unmodified, do not traverse it!" to the platform.
 * But subtrees "/rust_expanded_macros/<random_project_id>/..." can be omitted. There is a special
 * class for such fake, "dummy" directories - [FSItem.FSDir.DummyDir], and a method that replaces
 * a real directory to dummy - [makeDummy].
 *
 * So,
 * 1. prior to any operations with [MacroExpansionFileSystem], a list of all ever created
 *   "/rust_expanded_macros/<random_project_id>" directories should be loaded.
 *   This is done by [org.rust.lang.core.macros.MacroExpansionFileSystemRootsLoader.loadProjectDirs];
 * 2. when some project "foo" is closed, "/rust_expanded_macros/foo" should be replaced with
 *   a dummy directory using [makeDummy].
 */
class MacroExpansionFileSystem : LocalFileSystemBase() {
    private val root: FSDir = FSDir(null, "/")

    override fun getProtocol(): String = PROTOCOL
    override fun extractRootPath(path: String): String = "/"
    override fun normalize(path: String): String = path
    override fun getCanonicallyCasedName(file: VirtualFile): String = file.name
    override fun isCaseSensitive(): Boolean = true

    override fun isValidName(name: String): Boolean =
        PathUtilRt.isValidFileName(name, PathUtilRt.Platform.UNIX, false, null)

    @Throws(IOException::class)
    override fun createChildDirectory(requestor: Any?, parent: VirtualFile, dir: String): VirtualFile =
        throw UnsupportedOperationException()

    @Throws(IOException::class)
    override fun createChildFile(requestor: Any?, parent: VirtualFile, file: String): VirtualFile =
        throw UnsupportedOperationException()

    @Throws(IOException::class)
    override fun copyFile(requestor: Any?, file: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile =
        throw UnsupportedOperationException()

    @Throws(IOException::class)
    override fun deleteFile(requestor: Any?, file: VirtualFile): Unit =
        throw UnsupportedOperationException()

    @Throws(IOException::class)
    override fun moveFile(requestor: Any?, file: VirtualFile, newParent: VirtualFile): Unit =
        throw UnsupportedOperationException()

    @Throws(IOException::class)
    override fun renameFile(requestor: Any?, file: VirtualFile, newName: String): Unit =
        throw UnsupportedOperationException()

    private fun convert(file: VirtualFile): FSItem? {
        val parentFile = file.parent ?: return root

        val parentItem = convert(parentFile)
        return if (parentItem != null && parentItem is FSDir) {
            parentItem.findChild(file.name)
        } else {
            null
        }
    }

    private fun convert(path: String): FSItem? {
        val segments = StringUtil.split(path, "/")

        var file: FSItem = root
        for (segment in segments) {
            file = (file as? FSDir)?.findChild(segment) ?: return null
        }

        return file
    }

    override fun exists(fileOrDirectory: VirtualFile): Boolean {
        return convert(fileOrDirectory) != null
    }

    override fun list(file: VirtualFile): Array<String> {
        val fsItem = convert(file) as? FSDir ?: return ArrayUtil.EMPTY_STRING_ARRAY
        return fsItem.list()
    }

    override fun isDirectory(file: VirtualFile): Boolean {
        return convert(file) is FSDir
    }

    override fun getTimeStamp(file: VirtualFile): Long {
        val fsItem = convert(file) ?: return DEFAULT_TIMESTAMP
        return fsItem.timestamp
    }

    override fun setTimeStamp(file: VirtualFile, timeStamp: Long) {
        val fsItem = convert(file) ?: return
        fsItem.timestamp = if (timeStamp > 0) timeStamp else currentTimestamp()
    }

    override fun isWritable(file: VirtualFile): Boolean = true

    override fun setWritable(file: VirtualFile, writableFlag: Boolean) {}

    @Throws(IOException::class)
    override fun contentsToByteArray(file: VirtualFile): ByteArray {
        val fsItem = convert(file) ?: throw FileNotFoundException(file.path + " (No such file or directory)")
        if (fsItem !is FSFile) throw FileNotFoundException(file.path + " (Is a directory)")
        return fsItem.fetchAndRemoveContent()
            ?: throw FileNotFoundException(file.path + " (Content is not provided)")
    }

    @Throws(IOException::class)
    override fun getInputStream(file: VirtualFile): InputStream {
        return BufferExposingByteArrayInputStream(contentsToByteArray(file))
    }

    @Throws(IOException::class)
    override fun getOutputStream(file: VirtualFile,
                                 requestor: Any?,
                                 modStamp: Long,
                                 timeStamp: Long): OutputStream {
        throw UnsupportedOperationException()
    }

    override fun getLength(file: VirtualFile): Long {
        val fsItem = convert(file) as? FSFile ?: return DEFAULT_LENGTH
        return fsItem.length.toLong()
    }

    override fun getAttributes(file: VirtualFile): FileAttributes? {
        val item = convert(file) ?: return null
        val length = ((item as? FSFile)?.length ?: 0).toLong()
        return FileAttributes(item is FSDir, false, false, false, length, item.timestamp, true)
    }

    override fun replaceWatchedRoots(watchRequests: Collection<WatchRequest>,
                                     recursiveRoots: Collection<String>?,
                                     flatRoots: Collection<String>?): Set<WatchRequest> {
        throw IncorrectOperationException()
    }

    sealed class FSItem {
        abstract val parent: FSDir?
        abstract var name: String
        abstract var timestamp: Long

        protected fun bumpTimestamp() {
            timestamp = Math.min(currentTimestamp(), timestamp + 1)
        }

        override fun toString(): String = javaClass.simpleName + ": " + name

        open class FSDir(
            override var parent: FSDir?,
            override var name: String,

            @get:Synchronized
            @set:Synchronized
            override var timestamp: Long = currentTimestamp()
        ) : FSItem() {
            protected open val children: MutableList<FSItem> = mutableListOf()

            @Synchronized
            fun copyChildren(): List<FSItem> = ArrayList(children)

            @Synchronized
            fun findChild(name: String): FSItem? = children.find { it.name == name }

            @Synchronized
            fun addChild(item: FSItem, bump: Boolean = false, override: Boolean = false) {
                require(item.parent == this)
                require(item.name.isNotEmpty())
                if (override) {
                    children.removeIf { it.name == item.name }
                } else {
                    check(children.find { it.name == item.name } == null) { "File `${item.name}` already exists" }
                }
                children.add(item)
                if (bump) {
                    bumpTimestamp()
                }
            }

            fun addChildFile(name: String, bump: Boolean = false): FSFile =
                FSFile(this, name).also { addChild(it, bump) }

            fun addChildDir(name: String, bump: Boolean = false): FSDir =
                FSDir(this, name).also { addChild(it, bump) }

            @Synchronized
            fun removeChild(name: String, bump: Boolean = false) {
                children.removeIf { it.name == name }
                if (bump) {
                    bumpTimestamp()
                }
            }

            @Synchronized
            fun list(): Array<String> = children.map { it.name }.toTypedArray()

            @Synchronized
            fun clear(bump: Boolean = false) {
                children.clear()
                if (bump) {
                    bumpTimestamp()
                }
            }

            class DummyDir(
                parent: FSDir?,
                name: String,
                timestamp: Long = currentTimestamp()
            ) : FSDir(parent, name, timestamp) {
                override val children: MutableList<FSItem>
                    get() {
                        parent?.removeChild(name, bump = false)
                        return mutableListOf()
                    }
            }

            fun dummy(): FSDir = DummyDir(parent, name, timestamp)
        }

        class FSFile(
            override val parent: FSDir,
            override var name: String,

            @get:Synchronized
            @set:Synchronized
            override var timestamp: Long = currentTimestamp(),

            @get:Synchronized
            var length: Int = 0,

            @get:Synchronized
            var tempContent: ByteArray? = null
        ) : FSItem() {

            @Synchronized
            fun setContent(content: ByteArray) {
                tempContent = content
                length = content.size
                bumpTimestamp()
            }

            @Synchronized
            @Throws(FileNotFoundException::class)
            fun fetchAndRemoveContent(): ByteArray? {
                val tmp = tempContent ?: run {
                    parent.removeChild(name, bump = true)
                    return null
                }
                tempContent = null
                return tmp
            }
        }
    }

    private fun splitFilenameAndParent(path: String): Pair<String, String> {
        val index = path.lastIndexOf('/')
        check(index >= 0) { "$index" }
        val pathStart = path.substring(0, index)
        val filename = path.substring(index + 1)
        return pathStart to filename
    }

    fun createFileWithContent(path: String, content: String) {
        val (parentName, name) = splitFilenameAndParent(path)
        val parent = convert(parentName) ?: throw FileNotFoundException(parentName)
        check(parent is FSDir)
        val item = parent.addChildFile(name)
        item.setContent(content.toByteArray())
    }

    fun setFileContent(path: String, content: String) {
        val item = convert(path) ?: throw FileNotFoundException(path)
        check(item is FSFile)
        item.setContent(content.toByteArray())
    }

    fun createDirectory(path: String) {
        val (parentName, name) = splitFilenameAndParent(path)
        val parent = convert(parentName) ?: throw FileNotFoundException(parentName)
        check(parent is FSDir)
        parent.addChildDir(name, bump = true)
    }

    fun createDirectoryIfNotExistsOrDummy(path: String) {
        val (parentName, name) = splitFilenameAndParent(path)
        val parent = convert(parentName) ?: throw FileNotFoundException(parentName)
        check(parent is FSDir)
        val child = parent.findChild(name)
        if (child == null || child is FSDir.DummyDir) {
            if (child is FSDir.DummyDir) {
                parent.removeChild(name)
            }
            parent.addChildDir(name, bump = true)
        }
    }

    fun setDirectory(path: String, dir: FSDir, override: Boolean = true) {
        val (parentName, name) = splitFilenameAndParent(path)
        val parent = convert(parentName) ?: throw FileNotFoundException(parentName)
        check(parent is FSDir)
        dir.parent = parent
        dir.name = name
        parent.addChild(dir, bump = true, override = override)
    }

    fun makeDummy(path: String) {
        getDirectory(path)?.let { setDirectory(path, it.dummy()) }
    }

    fun getDirectory(path: String): FSDir? {
        return convert(path) as? FSDir
    }

    fun deleteFile(path: String) {
        val (parentName, name) = splitFilenameAndParent(path)
        val parent = convert(parentName) ?: return
        check(parent is FSDir)
        parent.removeChild(name, bump = true)
    }

    fun cleanDirectory(path: String, bump: Boolean = true) {
        val dir = convert(path) ?: throw FileNotFoundException(path)
        check(dir is FSDir)
        dir.clear(bump)
    }

    fun exists(path: String): Boolean = convert(path) != null
    fun isFile(path: String): Boolean = convert(path) is FSFile

    companion object {
        private const val PROTOCOL: String = "rust_macros"
        fun getInstance(): MacroExpansionFileSystem {
            return VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as MacroExpansionFileSystem
        }

        @Throws(IOException::class)
        fun writeFSItem(data: DataOutput, item: FSItem) {
            // TODO proper synchronization
            data.writeBoolean(item is FSDir)
            data.writeUTF(item.name)
            data.writeLong(item.timestamp)
            when (item) {
                is FSDir -> {
                    val children = item.copyChildren()
                    data.writeInt(children.size)
                    for (child in children) {
                        writeFSItem(data, child)
                    }
                }
                is FSFile -> {
                    val length = item.length
                    data.writeInt(length)
                    val content = item.tempContent?.takeIf { it.size == length }
                    data.writeBoolean(content != null)
                    if (content != null) {
                        data.write(content)
                    }
                }
            }
        }

        @Throws(IOException::class)
        fun readFSItem(data: DataInput, parent: FSDir?): FSItem {
            val isDir = data.readBoolean()
            val name = data.readUTF()
            val timestamp = data.readLong()
            return if (isDir) {
                val dir = FSDir(parent, name, timestamp)
                val count = data.readInt()
                for (i in 0 until count) {
                    val child = readFSItem(data, dir)
                    dir.addChild(child)
                }
                dir
            } else {
                val length = data.readInt()
                val hasContent = data.readBoolean()
                val content = if (hasContent) {
                    ByteArray(length).also { data.readFully(it) }
                } else {
                    null
                }
                FSFile(parent!!, name, timestamp, length, content)
            }
        }

        private fun currentTimestamp() = System.currentTimeMillis()
    }
}
