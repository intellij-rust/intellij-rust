/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir

object BspUtils {
    fun isBspProject(project: Project): Boolean =
        project.guessProjectDir()?.findChild(BspConstants.BSP_WORKSPACE) != null
}
