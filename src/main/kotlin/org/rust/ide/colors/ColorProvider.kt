/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.colors

import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.util.io.isAncestor
import org.rust.cargo.project.model.getTestSourceFolders
import org.rust.openapiext.pathAsPath
import java.awt.Color

class ColorProvider : EditorTabColorProvider {
    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? {
        return null
    }

    override fun getProjectViewColor(project: Project, file: VirtualFile): Color? {
        val testSourceDirs = getTestSourceFolders(project)
        val shouldBeMarked = testSourceDirs.any { it.file == file || it.file?.pathAsPath?.isAncestor(file.pathAsPath) == true }
        if (shouldBeMarked) {
            return JBColor(0xeffae7, 0x49544a)
        }
        return null
    }
}
