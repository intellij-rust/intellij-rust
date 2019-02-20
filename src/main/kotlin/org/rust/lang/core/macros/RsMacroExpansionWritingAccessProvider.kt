/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider

/** Protect macro expansions from being edited (e.g. by rename) */
class RsMacroExpansionWritingAccessProvider(val project: Project) : WritingAccessProvider() {
    /** @return set of files that cannot be accessed */
    override fun requestWriting(vararg files: VirtualFile): Collection<VirtualFile> {
        val service = project.macroExpansionManager
        return files.filter { service.isExpansionFile(it) }
    }

    override fun isPotentiallyWritable(file: VirtualFile): Boolean {
        return !project.macroExpansionManager.isExpansionFile(file)
    }

}
