package org.rust.bsp.toolwindow.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.Project
import org.rust.bsp.service.BspConnectionService
import org.rust.bsp.toolwindow.BspProjectTreeStructure
import org.rust.bsp.toolwindow.BspProjectsTree

class SelectAllAction(
    private val tree: BspProjectsTree,
    private val structure: BspProjectTreeStructure
) : AnAction("Select All Targets", "Selects all targets to be resolved during next refresh", AllIcons.Actions.Selectall) {

    override fun actionPerformed(e: AnActionEvent) {
        structure.selectAll()
        structure.checkStatus()
        tree.repaint()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project

        if (project != null) {
            doUpdate(project, e)
        } else {
            log.warn("DisconnectAction cannot be updated! Project not available.")
        }
    }

    private fun doUpdate(project: Project, e: AnActionEvent) {
        val connection = project.service<BspConnectionService>()
        e.presentation.isEnabled = connection.isConnected()
    }

    override fun getActionUpdateThread(): ActionUpdateThread =
        ActionUpdateThread.BGT

    private companion object {
        private val log = logger<SelectAllAction>()
    }
}
