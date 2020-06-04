/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile
import org.rust.lang.RsFileType

/**
 * Sets [FileType] to [RsFileType] for each file in [MacroExpansionFileSystem].
 * Used only as an optimization: default file type detection is slow, but we
 * know that any file in [MacroExpansionFileSystem] is [RsFileType]
 */
@Suppress("UnstableApiUsage")
class RsFileTypeOverriderForMacroExpansionFileSystem : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        return if (file !is StubVirtualFile && !file.isDirectory && file.fileSystem is MacroExpansionFileSystem) {
            RsFileType
        } else {
            null
        }
    }
}
