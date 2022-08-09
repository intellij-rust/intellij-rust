/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import org.jetbrains.annotations.TestOnly
import org.rust.lang.core.crate.CratePersistentId
import org.rust.lang.core.macros.MacroExpansionFileSystem.FSItem
import org.rust.openapiext.findNearestExistingFile

class MacroExpansionVfsBatch(private val contentRoot: String) {

    /**
     * Should be used if file was just created and there is no corresponding [VirtualFile] yet.
     * If we have [VirtualFile], then [VfsUtil.markDirty] should be used directly.
     */
    private val pathsToMarkDirty: MutableSet<String> = hashSetOf()

    var hasChanges: Boolean = false
        private set

    private val expansionFileSystem: MacroExpansionFileSystem = MacroExpansionFileSystem.getInstance()

    class Path(val path: String) {
        fun toVirtualFile(): VirtualFile? =
            MacroExpansionFileSystem.getInstance().findFileByPath(path)
    }

    fun createFile(crate: CratePersistentId, expansionName: String, content: String, implicit: Boolean = false): Path {
        val path = "${crate}/${expansionNameToPath(expansionName)}"
        return createFile(path, content, implicit)
    }

    fun createFile(relativePath: String, content: String, implicit: Boolean = false): Path {
        val path = "$contentRoot/$relativePath"
        if (implicit) {
            expansionFileSystem.createFileWithImplicitContent(path, content.toByteArray().size, mkdirs = true)
        } else {
            expansionFileSystem.createFileWithExplicitContent(path, content.toByteArray(), mkdirs = true)
        }
        val parent = path.substring(0, path.lastIndexOf('/'))
        pathsToMarkDirty += parent
        hasChanges = true
        return Path(path)
    }

    fun deleteFile(file: FSItem) {
        file.delete()

        val virtualFile = expansionFileSystem.findFileByPath(file.absolutePath())
        virtualFile?.let { markDirty(it) }
        hasChanges = true
    }

    @TestOnly
    fun deleteFile(file: VirtualFile) {
        MacroExpansionFileSystem.getInstance().deleteFile(file.path)
        markDirty(file)
        hasChanges = true
    }

    @TestOnly
    fun writeFile(file: VirtualFile, content: String) {
        MacroExpansionFileSystem.getInstance().setFileContent(file.path, content.toByteArray())
        markDirty(file)
        hasChanges = true
    }

    fun applyToVfs(async: Boolean, callback: Runnable? = null) {
        val root = expansionFileSystem.findFileByPath("/") ?: return

        for (path in pathsToMarkDirty) {
            markDirty(root.findNearestExistingFile(path).first)
        }

        RefreshQueue.getInstance().refresh(async, true, callback, root)
    }

    private fun markDirty(file: VirtualFile) {
        VfsUtil.markDirty(false, false, file)
    }
}
