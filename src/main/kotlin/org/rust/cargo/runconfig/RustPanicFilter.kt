package org.rust.cargo.runconfig

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Detects messages about panics and adds source code links to them.
 */
class RustPanicFilter(
    project: Project,
    cargoProjectDir: VirtualFile
) : RegexpFileLinkFilter(project, cargoProjectDir, "^\\s*thread '.+' panicked at '.+', $FILE_POSITION_RE$")
