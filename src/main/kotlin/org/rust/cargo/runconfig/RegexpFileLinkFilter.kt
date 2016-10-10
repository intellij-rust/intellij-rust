package org.rust.cargo.runconfig

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.filters.RegexpFilter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.utils.findFileByMaybeRelativePath
import java.io.File

/**
 * Base class for regexp-based output filters that extract
 * source code location from the output and add corresponding hyperlinks.
 */
open class RegexpFileLinkFilter(
    private val project: Project,
    private val cargoProjectDirectory: VirtualFile,
    expression: String
) : RegexpFilter(project, expression) {

    override fun createOpenFileHyperlink(fileName: String, line: Int, column: Int): HyperlinkInfo? {
        val platformNeutralName = fileName.replace(File.separatorChar, '/')
        return cargoProjectDirectory.findFileByMaybeRelativePath(platformNeutralName)?.let { file ->
            OpenFileHyperlinkInfo(project, file, line, column)
        }
    }
}
