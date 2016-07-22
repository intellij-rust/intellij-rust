package org.rust.ide.actions

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.workspace.CargoProjectWorkspaceListener
import org.rust.ide.notifications.subscribeForOneMessage
import org.rust.cargo.util.getComponentOrThrow
import org.rust.cargo.util.modulesWithCargoProject
import org.rust.ide.notifications.showBalloon
import org.rust.ide.utils.isNullOrEmpty

class RefreshCargoProjectAction : AnAction() {
    init {
        templatePresentation.text = "Refresh Cargo project"
        templatePresentation.description = "Update Cargo project information and download new dependencies"
    }

    override fun update(e: AnActionEvent) {
        if (e.project?.toolchain == null || e.project?.modulesWithCargoProject.isNullOrEmpty()) {
            e.presentation.isEnabled = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolchain = project.toolchain ?: return
        val modules = project.modulesWithCargoProject.orEmpty()
        if (modules.isEmpty()) return

        ApplicationManager.getApplication().saveAll()
        for (module in modules) {
            val workspace = module.getComponentOrThrow<CargoProjectWorkspace>()

            subscribeForOneMessage(module.messageBus, CargoProjectWorkspaceListener.Topics.UPDATES, object : CargoProjectWorkspaceListener {
                override fun onWorkspaceUpdateCompleted(r: CargoProjectWorkspaceListener.UpdateResult) {
                    val (type, content) = when (r) {
                        is CargoProjectWorkspaceListener.UpdateResult.Ok ->
                            NotificationType.INFORMATION to "Project '${module.name}' successfully updated!"

                        is CargoProjectWorkspaceListener.UpdateResult.Err ->
                            NotificationType.ERROR to "Project '${module.name}' failed to update.<br> ${r.error.message}"
                    }

                    project.showBalloon(content, type)
                }

            })

            workspace.requestUpdateUsing(toolchain)
        }
    }
}
