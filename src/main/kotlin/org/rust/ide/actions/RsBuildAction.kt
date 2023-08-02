/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.task.ProjectTaskManager
import com.intellij.util.PlatformUtils
import org.rust.cargo.runconfig.buildProject
import org.rust.cargo.runconfig.hasCargoProject
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled
import org.rust.openapiext.project

class RsBuildAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        performForContext(e.dataContext)
    }

    @VisibleForTesting
    fun performForContext(e: DataContext) {
        val project = e.project ?: return
        if (isFeatureEnabled(RsExperiments.BUILD_TOOL_WINDOW)) {
            ProjectTaskManager.getInstance(project).buildAllModules()
        } else {
            project.buildProject()
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = isSuitablePlatform() && e.project?.hasCargoProject == true
    }

    companion object {
        private fun isSuitablePlatform(): Boolean {
            return !(PlatformUtils.isIntelliJ() || PlatformUtils.isAppCode() || PlatformUtils.isCLion())
        }
    }
}
