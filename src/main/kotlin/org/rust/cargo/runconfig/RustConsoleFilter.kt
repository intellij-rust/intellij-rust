package org.rust.cargo.runconfig

import com.intellij.execution.filters.RegexpFilter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Detects source code locations in rustc output and adds links to them.
 */
class RustConsoleFilter(
    project: Project,
    cargoProjectDir: VirtualFile
) : RegexpFileLinkFilter(
    project,
    cargoProjectDir,
    "(?: --> )?${RegexpFilter.FILE_PATH_MACROS}:${RegexpFilter.LINE_MACROS}:${RegexpFilter.COLUMN_MACROS}") {
}
