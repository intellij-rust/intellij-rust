/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.rust.checkMacroExpansionFileSystemAfterTest

class MacroExpansionFileSystemTest : BasePlatformTestCase() {
    fun `test simple`() {
        batch {
            createFile("/foo", "bar.txt", "bar content")
            createFile("/foo", "baz.txt", "baz content")
        }
        val vfs = MacroExpansionFileSystem.getInstance()
        val foo = vfs.findNonNullFileByPath("/foo")
        val bar = vfs.findNonNullFileByPath("/foo/bar.txt")
        val baz = vfs.findNonNullFileByPath("/foo/baz.txt")
        assertEquals("bar content", VfsUtil.loadText(bar))
        assertEquals("baz content", VfsUtil.loadText(baz))
        assertEquals("bar.txt", bar.name)
        assertEquals(foo, bar.parent)
        assertEquals(2, foo.children.size)
        assertContainsElements(foo.children.toList(), bar, baz)
        assertEquals(vfs.findNonNullFileByPath("/foo"), foo)
        assertEquals(vfs.findNonNullFileByPath("/foo/"), foo)
        assertEquals(vfs.findNonNullFileByPath("/"), foo.parent)

        batch { writeFile(bar, "new bar content") }
        assertEquals("new bar content", VfsUtil.loadText(bar))
        assertEquals("baz content", VfsUtil.loadText(baz))

        batch { deleteFile(bar) }
        assertNull(vfs.findFileByPath("/foo/bar.txt"))
        assertNotNull(vfs.findFileByPath("/foo/baz.txt"))

        batch { deleteFile(foo) }
        assertNull(vfs.findFileByPath("/foo/baz.txt"))
        assertNull(vfs.findFileByPath("/foo"))
    }

    override fun tearDown() {
        super.tearDown()
        checkMacroExpansionFileSystemAfterTest()
    }
}

private fun VirtualFileSystem.findNonNullFileByPath(path: String): VirtualFile =
    findFileByPath(path) ?: error("File not found: $path")

private fun batch(action: VfsBatch.() -> Unit) {
    val batch = VfsBatch()
    batch.action()
    batch.applyToVfs(async = false)
}
