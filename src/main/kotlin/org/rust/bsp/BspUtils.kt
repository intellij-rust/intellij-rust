/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir

class BspUtils {
    companion object {
        fun isBspProject(project: Project): Boolean {
            return project.guessProjectDir()?.findChild(BspConstants.BSP_WORKSPACE) != null
        }
    }
}
