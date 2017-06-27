/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.filters

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Detects source code locations in rustc output and adds links to them.
 */
class RsConsoleFilter(
    project: Project,
    cargoProjectDir: VirtualFile
) : RegexpFileLinkFilter(
    project,
    cargoProjectDir,
    "(?:\\s+--> )?${FILE_POSITION_RE}.*"
)
