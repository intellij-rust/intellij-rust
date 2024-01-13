package org.rust.bsp.toolwindow.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.Project
import org.rust.bsp.service.BspConnectionService
import org.rust.ide.actions.RefreshCargoProjectsAction

class RefreshAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project

        if (project != null) {
            RefreshCargoProjectsAction().actionPerformed(e)
        } else {
            log.warn("RefreshAction cannot be performed! Project not available.")
        }
    }


    override fun update(e: AnActionEvent) {
        val project = e.project

        if (project != null) {
            doUpdate(project, e)
        } else {
            log.warn("RefreshAction cannot be updated! Project not available.")
        }
    }

    private fun doUpdate(project: Project, e: AnActionEvent) {
        val connection = project.service<BspConnectionService>()
        e.presentation.isEnabled = connection.isConnected()
    }

    override fun getActionUpdateThread(): ActionUpdateThread =
        ActionUpdateThread.BGT

    private companion object {
        private val log = logger<RefreshAction>()
    }
}
