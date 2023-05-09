package org.jetbrains.plugins.bsp.ui.widgets.tool.window.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.bsp.server.connection.BspConnectionService
import org.jetbrains.plugins.bsp.server.tasks.CollectProjectDetailsTask
import org.jetbrains.plugins.bsp.ui.console.BspConsoleService
import org.jetbrains.plugins.bsp.ui.widgets.tool.window.all.targets.BspAllTargetsWidgetBundle

public class ConnectAction : AnAction(BspAllTargetsWidgetBundle.message("connect.action.text")) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project

    if (project != null) {
      doAction(project)
    } else {
      log.warn("ConnectAction cannot be performed! Project not available.")
    }
  }

  private fun doAction(project: Project) {
    val bspSyncConsole = BspConsoleService.getInstance(project).bspSyncConsole
    bspSyncConsole.startTask("bsp-connect", "Connect", "Connecting...")

    val collectProjectDetailsTask = CollectProjectDetailsTask(project, "bsp-connect").prepareBackgroundTask()
    collectProjectDetailsTask.executeInTheBackground(
      name = "Connecting...",
      cancelable = true,
      beforeRun = { BspConnectionService.getInstance(project).value.connect("bsp-connect") },
      afterOnSuccess = { bspSyncConsole.finishTask("bsp-connect", "Done!") }
    )
  }

  public override fun update(e: AnActionEvent) {
    val project = e.project

    if (project != null) {
      doUpdate(project, e)
    } else {
      log.warn("ConnectAction cannot be updated! Project not available.")
    }
  }

  private fun doUpdate(project: Project, e: AnActionEvent) {
    val bspConnection = BspConnectionService.getInstance(project).value
    e.presentation.isEnabled = bspConnection.isConnected() == false
  }

  override fun getActionUpdateThread(): ActionUpdateThread =
    ActionUpdateThread.BGT

  private companion object {
    private val log = logger<ConnectAction>()
  }
}
