package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.isNullOrEmpty
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.project.workspace.CargoProjectWorkspaceListener
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.getComponentOrThrow
import org.rust.cargo.util.modules

class RefreshCargoProjectAction : AnAction() {
    init {
        templatePresentation.text = "Refresh Cargo project"
        templatePresentation.description = "Update Cargo project information and download new dependencies"
    }

    private val Project.modulesWithToolchains: List<Pair<Module, RustToolchain>>
        get() = modules.mapNotNull { module ->
            module.toolchain?.let { Pair(module, it) }
        }

    override fun update(e: AnActionEvent) {
        if (e.project?.modulesWithToolchains.isNullOrEmpty()) {
            e.presentation.isEnabled = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val modules = e.project?.modulesWithToolchains
        if (modules.isNullOrEmpty()) return
        ApplicationManager.getApplication().saveAll()
        for ((module, toolchain) in modules.orEmpty()) {
            val workspace = module.getComponentOrThrow<CargoProjectWorkspace>()

            Notifier(module).let {
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
    private class Notifier(val module: Module) : CargoProjectWorkspaceListener {

        val connectionDisposer = Disposer.newDisposable()

        override fun onWorkspaceUpdateCompleted(r: CargoProjectWorkspaceListener.UpdateResult) {
            when (r) {
                is CargoProjectWorkspaceListener.UpdateResult.Ok  -> showBalloon("Project '${module.project.name}' successfully updated!", MessageType.INFO)
                is CargoProjectWorkspaceListener.UpdateResult.Err -> showBalloon("Project '${module.project.name}' update failed: ${r.error.message}", MessageType.ERROR)
            }

            Disposer.dispose(connectionDisposer)
        }

        private fun showBalloon(message: String, type: MessageType) {
            PopupUtil.showBalloonForActiveComponent(message, type)
        }
    }
}
