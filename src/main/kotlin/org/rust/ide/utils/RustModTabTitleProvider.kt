package org.rust.ide.utils

import com.intellij.openapi.fileEditor.impl.UniqueNameEditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.lang.core.psi.impl.isRustFile

class RustModTabTitleProvider : UniqueNameEditorTabTitleProvider() {
    val MOD_FILE_NAME = "mod.rs"

    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        if (file.isRustFile && MOD_FILE_NAME == file.name) {
            return super.getEditorTabTitle(project, file) ?:
                "${file.parent.name}/${file.name}"
        }

        return null
    }
}
