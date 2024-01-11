/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.project.Project
import com.intellij.testFramework.DumbModeTestUtils

object DumbModeTestUtil {
    fun startEternalDumbModeTask(project: Project): Token {
        return Token(project, DumbModeTestUtils.startEternalDumbModeTask(project))
    }

    class Token(
        private val project: Project,
        private val token: DumbModeTestUtils.EternalTaskShutdownToken
    ) : AutoCloseable {
        override fun close() {
            DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token)
        }
    }
}
