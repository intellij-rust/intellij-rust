package org.rust.bsp.toolwindow.actions

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.CompileParams
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.components.services
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.Project
import org.rust.bsp.BspBuildTask
import org.rust.bsp.service.BspConnectionService
import org.rust.bsp.service.BspProjectViewService
import org.rust.taskQueue

class BuildAllAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project

        if (project != null) {
            doAction(project)
        } else {
            log.warn("BuildAllAction cannot be performed! Project not available.")
        }
    }

    private fun doAction(project: Project) {
        val bspProjectViewService = project.service<BspProjectViewService>()
        val buildTask = BspBuildTask(project, bspProjectViewService.getActiveTargets().map { BuildTargetIdentifier(it) })
        project.taskQueue.run(buildTask)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project

        if (project != null) {
            doUpdate(project, e)
        } else {
            log.warn("BuildAllAction cannot be updated! Project not available.")
        }
    }

    private fun doUpdate(project: Project, e: AnActionEvent) {
        val connection = project.service<BspConnectionService>()
        e.presentation.isEnabled = connection.isConnected()
    }

    override fun getActionUpdateThread(): ActionUpdateThread =
        ActionUpdateThread.BGT

    private companion object {
        private val log = logger<BuildAllAction>()
    }
}
