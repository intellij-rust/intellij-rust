/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

class RsConsoleRunnerFactory {

    fun createConsoleRunner(project: Project, contextModule: Module?): RsConsoleRunner {
        return RsConsoleRunner(project, TOOL_WINDOW_TITLE)
    }

    companion object {
        const val TOOL_WINDOW_TITLE: String = "Rust Console"

        fun getInstance(): RsConsoleRunnerFactory =
            ApplicationManager.getApplication().getComponent(RsConsoleRunnerFactory::class.java)
    }
}
