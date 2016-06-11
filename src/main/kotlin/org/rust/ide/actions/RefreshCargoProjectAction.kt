package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.workspace.CargoProjectWorkspaceListener
import org.rust.cargo.util.getComponentOrThrow
import org.rust.cargo.util.modulesWithCargoProject
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

            Notifier(project).let {
                module.messageBus
                    .connect(it.connectionDisposer)
                    .subscribe(CargoProjectWorkspaceListener.Topics.UPDATES, it)

                workspace.requestUpdateUsing(toolchain, immediately = true)
            }
        }
    }

    /**
     * Project-update task's notifier
     */
    private class Notifier(val project: Project) : CargoProjectWorkspaceListener {

        val connectionDisposer = Disposer.newDisposable()

        override fun onWorkspaceUpdateCompleted(r: CargoProjectWorkspaceListener.UpdateResult) {
            when (r) {
                is CargoProjectWorkspaceListener.UpdateResult.Ok  -> showBalloon("Project '${project.name}' successfully updated!", MessageType.INFO)
                is CargoProjectWorkspaceListener.UpdateResult.Err -> showBalloon("Project '${project.name}' update failed: ${r.error.message}", MessageType.ERROR)
            }

            Disposer.dispose(connectionDisposer)
        }

        private fun showBalloon(message: String, type: MessageType) {
            PopupUtil.showBalloonForActiveComponent(message, type)
        }
    }
}
