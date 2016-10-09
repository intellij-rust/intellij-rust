package org.rust.cargo.runconfig

import com.intellij.execution.filters.RegexpFilter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Detects messages about panics and adds source code links to them.
 */
class RustBacktraceFilter(
    project: Project,
    cargoProjectDir: VirtualFile
) : RegexpFileLinkFilter(
    project,
    cargoProjectDir,
    "^\\s+at ${RegexpFilter.FILE_PATH_MACROS}:${RegexpFilter.LINE_MACROS}$") {
}
