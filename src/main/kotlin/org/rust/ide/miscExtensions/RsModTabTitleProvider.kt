/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.miscExtensions

import com.intellij.openapi.fileEditor.impl.UniqueNameEditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.lang.core.psi.isRustFile
import java.io.File

class RsModTabTitleProvider : UniqueNameEditorTabTitleProvider() {
    val MOD_FILE_NAME = "mod.rs"

    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        if (file.isRustFile && MOD_FILE_NAME == file.name) {
            return super.getEditorTabTitle(project, file) ?:
                "${file.parent.name}${File.separator}${file.name}"
        }

        return null
    }
}
