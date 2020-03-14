/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import org.rust.lang.core.macros.MACRO_EXPANSION_VFS_ROOT
import org.rust.lang.core.macros.MacroExpansionFileSystem

fun checkMacroExpansionFileSystemAfterTest() {
    val vfs = MacroExpansionFileSystem.getInstance()
    val rootPath = "/$MACRO_EXPANSION_VFS_ROOT"
    if (vfs.exists(rootPath)) {
        val incorrectFilePaths = vfs.getDirectory(rootPath)?.copyChildren().orEmpty()
            .filter { it !is MacroExpansionFileSystem.FSItem.FSDir.DummyDir }
            .map { rootPath + "/" + it.name }

        if (incorrectFilePaths.isNotEmpty()) {
            for (path in incorrectFilePaths) {
                MacroExpansionFileSystem.getInstance().deleteFile(path)
            }
            error("$incorrectFilePaths are not dummy dirs")
        }
    }
    val incorrectFilePaths = vfs.getDirectory("/")?.copyChildren().orEmpty()
        .filter { it.name != MACRO_EXPANSION_VFS_ROOT }
        .map { "/" + it.name }

    if (incorrectFilePaths.isNotEmpty()) {
        for (path in incorrectFilePaths) {
            MacroExpansionFileSystem.getInstance().deleteFile(path)
        }
        error("$incorrectFilePaths should have been removed at the end of the test")
    }
}
