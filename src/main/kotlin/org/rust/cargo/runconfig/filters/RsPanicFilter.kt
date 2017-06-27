/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.filters

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Detects messages about panics and adds source code links to them.
 */
class RsPanicFilter(
    project: Project,
    cargoProjectDir: VirtualFile
) : RegexpFileLinkFilter(project, cargoProjectDir, "\\s*thread '.+' panicked at '.+', ${FILE_POSITION_RE}")
