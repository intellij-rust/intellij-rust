/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project

@Suppress("UnstableApiUsage")
object DumbModeTestUtil {
    fun startEternalDumbModeTask(project: Project): Token {
        val dumbService = DumbServiceImpl.getInstance(project)
        val oldValue = dumbService.isDumb
        dumbService.isDumb = true
        return Token(dumbService, oldValue)
    }

    class Token(
        private val dumbService: DumbServiceImpl,
        private val oldValue: Boolean,
    ) : AutoCloseable {
        override fun close() {
            dumbService.isDumb = oldValue
        }
    }
}
