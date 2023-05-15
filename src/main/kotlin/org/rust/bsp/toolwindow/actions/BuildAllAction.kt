package org.rust.bsp.toolwindow.actions

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.bsp4j.CompileParams
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.Project
import org.rust.bsp.service.BspConnectionService

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
    runBackgroundableTask("Building...", project = project, cancellable = false) {
      val connection = project.service<BspConnectionService>()
      connection.compileAllSolutions(CompileParams(listOf()))
    }
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
      e.presentation.isEnabled = connection.isConnected() == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread =
    ActionUpdateThread.BGT

  private companion object {
    private val log = logger<BuildAllAction>()
  }
}
