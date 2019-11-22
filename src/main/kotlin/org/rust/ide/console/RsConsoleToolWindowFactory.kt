/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled

class RsConsoleToolWindowFactory : ToolWindowFactory, Condition<Project>, DumbAware {

    override fun value(t: Project?): Boolean = isFeatureEnabled(RsExperiments.REPL_TOOL_WINDOW)

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        RsConsoleToolWindow.getInstance(project).initStateListener()

        val runner = RsConsoleRunnerFactory.getInstance().createConsoleRunner(project, null)
        TransactionGuard.submitTransaction(project, Runnable { runner.runSync(true) })
    }

    companion object {
        const val ID: String = "Rust Console"
    }
}
