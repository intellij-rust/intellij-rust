package org.rust.cargo.runconfig

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.filters.RegexpFilter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File


class RustConsoleFilter(
    private val project: Project,
    private val cargoProjectDirectory: VirtualFile
) : RegexpFilter(project, "${RegexpFilter.FILE_PATH_MACROS}:${RegexpFilter.LINE_MACROS}:${RegexpFilter.COLUMN_MACROS}") {

    override fun createOpenFileHyperlink(fileName: String, line: Int, column: Int): HyperlinkInfo? {
        val platformNeutralName = fileName.replace(File.separatorChar, '/')
        return cargoProjectDirectory.findFileByRelativePath(platformNeutralName)?.let { file ->
            OpenFileHyperlinkInfo(project, file, line, column)
        }
    }
}

