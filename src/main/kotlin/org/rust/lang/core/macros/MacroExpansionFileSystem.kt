/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.util.io.FileAttributes.CaseSensitivity
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import com.intellij.openapi.vfs.newvfs.VfsImplUtil
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.PathUtilRt
import org.rust.lang.core.macros.MacroExpansionFileSystem.Companion.readFSItem
import org.rust.lang.core.macros.MacroExpansionFileSystem.Companion.writeFSItem
import org.rust.lang.core.macros.MacroExpansionFileSystem.FSItem
import org.rust.lang.core.macros.MacroExpansionFileSystem.FSItem.FSDir
import org.rust.lang.core.macros.MacroExpansionFileSystem.FSItem.FSFile
import org.rust.openapiext.isUnitTestMode
import java.io.*
import kotlin.math.max

/**
 * An implementation of [com.intellij.openapi.vfs.VirtualFileSystem] used to store macro expansions.
 *
 * The problem is that we need to store tens of thousands of small files (with macro expansions).
 * A real filesystem is slow, and a lot of small files consumes too much of diskspace.
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
 * "/rust_expanded_macros/<random_project_id>/<crate_id>/a/b/<mix_hash>_<order>.rs", where "<random_project_id>" is a
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
class MacroExpansionFileSystem : NewVirtualFileSystem() {
    private val root: FSDir = FSDir(null, "/")

    override fun getProtocol(): String = PROTOCOL
    override fun extractRootPath(path: String): String = "/"
    override fun normalize(path: String): String = path
    override fun getCanonicallyCasedName(file: VirtualFile): String = file.name
    override fun isCaseSensitive(): Boolean = true
    override fun isReadOnly(): Boolean = false
    override fun getRank(): Int = 1
    override fun refresh(asynchronous: Boolean) = VfsImplUtil.refresh(this, asynchronous)
    override fun refreshAndFindFileByPath(path: String): VirtualFile? = VfsImplUtil.refreshAndFindFileByPath(this, path)
    override fun findFileByPath(path: String): VirtualFile? = VfsImplUtil.findFileByPath(this, path)
    override fun findFileByPathIfCached(path: String): VirtualFile? = VfsImplUtil.findFileByPathIfCached(this, path)

    override fun isValidName(name: String): Boolean =
        PathUtilRt.isValidFileName(name, PathUtilRt.Platform.UNIX, false, null)

    @Throws(IOException::class)
    override fun createChildDirectory(requestor: Any?, parent: VirtualFile, dir: String): VirtualFile {
        if (requestor != TrustedRequestor) {
            throw UnsupportedOperationException()
        }
        val parentFsDir = convert(parent) ?: throw FileNotFoundException("${parent.path} (No such file or directory)")
        if (parentFsDir !is FSDir) throw IOException("${parent.path} is not a directory")
        val existingDir = parentFsDir.findChild(dir)
        if (existingDir == null) {
            parentFsDir.addChildDir(dir, bump = true)
        } else if (existingDir !is FSDir) {
            throw IOException("Directory already contains a file named $dir")
        }
        return FakeVirtualFile(parent, dir)
    }

    @Throws(IOException::class)
    override fun createChildFile(requestor: Any?, parent: VirtualFile, file: String): VirtualFile =
        throw UnsupportedOperationException()

    @Throws(IOException::class)
    override fun copyFile(requestor: Any?, file: VirtualFile, newParent: VirtualFile, copyName: String): VirtualFile =
        throw UnsupportedOperationException()

    @Throws(IOException::class)
    override fun deleteFile(requestor: Any?, file: VirtualFile) {
        if (requestor != TrustedRequestor) {
            throw UnsupportedOperationException()
        }
        val fsItem = convert(file) ?: return
        val parent = fsItem.parent ?: throw IOException("Can't delete root (${file.path})")
        parent.removeChild(fsItem.name, bump = true)
    }

    @Throws(IOException::class)
    override fun moveFile(requestor: Any?, file: VirtualFile, newParent: VirtualFile) {
        if (requestor != TrustedRequestor) {
            throw UnsupportedOperationException()
        }
        if (file.parent == newParent) {
            throw IOException("Cannot move file `${file.path}` to the same directory where it is already located")
        }
        val fsItem = convert(file) ?: throw FileNotFoundException("${file.path} (No such file or directory)")
        val newParentFsDir = convert(newParent) ?: throw FileNotFoundException("${newParent.path} (No such file or directory)")
        if (newParentFsDir !is FSDir) throw IOException("${newParent.path} is not a directory")
        if (newParentFsDir.findChild(file.name) != null) {
            throw IOException("Directory already contains a file named ${file.name}")
        }
        val oldParentFsDir = fsItem.parent ?: throw IOException("Can't move root (${file.path})")
        oldParentFsDir.removeChild(fsItem.name, bump = true)
        fsItem.parent = newParentFsDir
        newParentFsDir.addChild(fsItem, bump = true)
    }

    @Throws(IOException::class)
    override fun renameFile(requestor: Any?, file: VirtualFile, newName: String) {
        if (requestor != TrustedRequestor) {
            throw UnsupportedOperationException()
        }
        val fsItem = convert(file) ?: throw FileNotFoundException("${file.path} (No such file or directory)")
        val parent = fsItem.parent ?: throw IOException("Can't rename root (${file.path})")
        parent.removeChild(fsItem.name)
        fsItem.name = newName
        parent.addChild(fsItem, bump = true)
    }

    private fun convert(file: VirtualFile): FSItem? {
        val parentFile = file.parent ?: return root

        val parentItem = convert(parentFile)
        return if (parentItem != null && parentItem is FSDir) {
            parentItem.findChild(file.name)
        } else {
            null
        }
    }

    private fun convert(path: String, mkdirs: Boolean = false): FSItem? {
        var file: FSItem = root
        for (segment in StringUtil.split(path, "/")) {
            if (file !is FSDir) return null
            file = file.findChild(segment)
                ?: if (mkdirs) file.addChildDir(segment) else return null
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

    @Suppress("IfThenToElvis")
    @Throws(IOException::class)
    override fun contentsToByteArray(file: VirtualFile): ByteArray {
        val fsItem = convert(file) ?: throw FileNotFoundException(file.path + " (No such file or directory)")
        if (fsItem !is FSFile) throw FileNotFoundException(file.path + " (Is a directory)")
        return fsItem.fetchAndRemoveContent() ?: run {
            val (mixHash, storedVersion) = file.extractMixHashAndMacroStorageVersion() ?: (null to -1)
            val cachedExpansion = mixHash
                ?.takeIf { storedVersion == MACRO_STORAGE_VERSION }
                ?.let { MacroExpansionSharedCache.getInstance().getExpansionIfCached(it)?.ok() }
            if (cachedExpansion != null) {
                cachedExpansion.text.toByteArray()
            } else if (storedVersion != MACRO_STORAGE_VERSION) {
                // Found old version -> the file will be deleted by `MacroExpansionTask`
                ArrayUtil.EMPTY_BYTE_ARRAY
            } else {
                fsItem.delete()
                val e = FileNotFoundException(file.path + " (Content is not provided)")
                MACRO_LOG.warn("The file content has already been fetched", e)
                throw e
            }
        }
    }

    @Throws(IOException::class)
    override fun getInputStream(file: VirtualFile): InputStream {
        return BufferExposingByteArrayInputStream(contentsToByteArray(file))
    }

    @Throws(IOException::class)
    override fun getOutputStream(
        file: VirtualFile,
        requestor: Any?,
        modStamp: Long,
        timeStamp: Long
    ): OutputStream {
        if (requestor != TrustedRequestor && !isWritingAllowed(file)) {
            throw UnsupportedOperationException()
        }
        return object : ByteArrayOutputStream() {
            @Throws(IOException::class)
            override fun close() {
                super.close()
                val fsItem = convert(file)
                if (fsItem !is FSFile) throw IOException("The file is a directory (${file.path}")
                fsItem.length = size()
                setTimeStamp(file, timeStamp)
            }
        }
    }

    override fun getLength(file: VirtualFile): Long {
        val fsItem = convert(file) as? FSFile ?: return DEFAULT_LENGTH
        return fsItem.length.toLong()
    }

    override fun getAttributes(file: VirtualFile): FileAttributes? {
        val item = convert(file) ?: return null
        val length = ((item as? FSFile)?.length ?: 0).toLong()
        val isDir = item is FSDir
        val caseSensitivity = if (isDir) CaseSensitivity.SENSITIVE else CaseSensitivity.UNKNOWN
        return FileAttributes(isDir, false, false, false, length, item.timestamp, true, caseSensitivity)
    }

    sealed class FSItem {
        abstract var parent: FSDir?
        abstract var name: String
        abstract var timestamp: Long

        fun bumpTimestamp() {
            timestamp = max(currentTimestamp(), timestamp + 1)
        }

        fun delete() {
            parent?.removeChild(name, bump = true)
        }

        fun absolutePath(): String =
            buildString {
                fun FSItem.go() {
                    parent?.go()
                    append('/')
                    append(name)
                }
                go()
            }

        override fun toString(): String = javaClass.simpleName + ": " + name

        open class FSDir(
            @get:Synchronized
            @set:Synchronized
            override var parent: FSDir?,

            @get:Synchronized
            @set:Synchronized
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
                    if (children.any { it.name == item.name }) {
                        throw FSItemAlreadyExistsException("File `${item.name}` already exists")
                    }
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
                        if (isUnitTestMode) error("DummyDir should not be touched!")
                        parent?.removeChild(name, bump = false)
                        return mutableListOf()
                    }
            }

            fun dummy(): FSDir = DummyDir(parent, name, timestamp)
        }

        class FSFile(
            @get:Synchronized
            @set:Synchronized
            override var parent: FSDir?,

            @get:Synchronized
            @set:Synchronized
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
            fun setImplicitContent(fileSize: Int) {
                tempContent = null
                length = fileSize
                bumpTimestamp()
            }

            @Synchronized
            fun fetchAndRemoveContent(): ByteArray? {
                val tmp = tempContent
                tempContent = null
                return tmp
            }
        }
    }

    private fun splitFilenameAndParent(path: String): Pair<String, String> {
        val index = path.lastIndexOf('/')
        if (index < 0) throw IllegalPathException(path)
        val pathStart = path.substring(0, index)
        val filename = path.substring(index + 1)
        return pathStart to filename
    }

    @Throws(FSException::class)
    fun createFileWithExplicitContent(path: String, content: ByteArray, mkdirs: Boolean = false) {
        createFileWithoutContent(path, mkdirs).setContent(content)
    }

    @Throws(FSException::class)
    fun createFileWithImplicitContent(path: String, fileSize: Int, mkdirs: Boolean = false) {
        createFileWithoutContent(path, mkdirs).setImplicitContent(fileSize)
    }

    @Throws(FSException::class)
    fun createFileWithoutContent(path: String, mkdirs: Boolean = false): FSFile {
        val (parentName, name) = splitFilenameAndParent(path)
        val parent = convert(parentName, mkdirs) ?: throw FSItemNotFoundException(parentName)
        if (parent !is FSDir) throw FSItemIsNotADirectoryException(path)
        return parent.addChildFile(name)
    }

    @Throws(FSException::class)
    fun setFileContent(path: String, content: ByteArray) {
        val item = convert(path) ?: throw FSItemNotFoundException(path)
        if (item !is FSFile) throw FSItemIsADirectoryException(path)
        item.setContent(content)
    }

    @Throws(FSException::class)
    fun createDirectoryIfNotExistsOrDummy(path: String) {
        val (parentName, name) = splitFilenameAndParent(path)
        val parent = convert(parentName) ?: throw FSItemNotFoundException(parentName)
        if (parent !is FSDir) throw FSItemIsNotADirectoryException(path)
        val child = parent.findChild(name)
        if (child == null || child is FSDir.DummyDir) {
            if (child is FSDir.DummyDir) {
                parent.removeChild(name)
            }
            parent.addChildDir(name, bump = true)
        }
    }

    @Throws(FSException::class)
    fun setDirectory(path: String, dir: FSDir, override: Boolean = true) {
        val (parentName, name) = splitFilenameAndParent(path)
        val parent = convert(parentName) ?: throw FSItemNotFoundException(parentName)
        if (parent !is FSDir) throw FSItemIsNotADirectoryException(path)
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
        if (parent !is FSDir) throw FSItemIsNotADirectoryException(path)
        parent.removeChild(name, bump = true)
    }

    fun cleanDirectoryIfExists(path: String, bump: Boolean = true) {
        val dir = convert(path) ?: return
        if (dir !is FSDir) throw FSItemIsNotADirectoryException(path)
        dir.clear(bump)
    }

    fun exists(path: String): Boolean = convert(path) != null

    open class FSException(path: String) : RuntimeException(path)
    class FSItemNotFoundException(path: String) : FSException(path)
    class FSItemIsNotADirectoryException(path: String) : FSException(path)
    class FSItemIsADirectoryException(path: String) : FSException(path)
    class FSItemAlreadyExistsException(path: String) : FSException(path)
    class IllegalPathException(path: String) : FSException(path)

    object TrustedRequestor

    companion object {
        private const val PROTOCOL: String = "rust-macros"
        private val RUST_MACROS_ALLOW_WRITING: Key<Boolean> = Key.create("RUST_MACROS_ALLOW_WRITING")

        fun getInstance(): MacroExpansionFileSystem {
            return VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as MacroExpansionFileSystem
        }

        /** null during the plugin unloading */
        fun getInstanceOrNull(): MacroExpansionFileSystem? {
            return VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as? MacroExpansionFileSystem
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

        fun <T> withAllowedWriting(file: VirtualFile, f: () -> T): T {
            setAllowWriting(file, true)
            try {
                return f()
            } finally {
                setAllowWriting(file, false)
            }
        }

        fun isWritingAllowed(file: VirtualFile): Boolean =
            file.getUserData(RUST_MACROS_ALLOW_WRITING) == true

        private fun setAllowWriting(file: VirtualFile, allow: Boolean) {
            file.putUserData(RUST_MACROS_ALLOW_WRITING, if (allow) true else null)
        }

        private fun currentTimestamp() = System.currentTimeMillis()
    }
}
