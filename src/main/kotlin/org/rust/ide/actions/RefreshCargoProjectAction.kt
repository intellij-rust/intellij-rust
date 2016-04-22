package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.containers.isNullOrEmpty
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.getModules
import org.rust.cargo.util.getServiceOrThrow

class RefreshCargoProjectAction : AnAction() {
    init {
        templatePresentation.text = "Refresh Cargo project"
        templatePresentation.description = "Update Cargo project information and download new dependencies"
    }

    private val Project.modulesWithToolchains: List<Pair<Module, RustToolchain>>
        get() = getModules().orEmpty().mapNotNull { module ->
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
            val service = module.getServiceOrThrow<CargoProjectWorkspace>()
            service.requestUpdateUsing(toolchain, immediately = true)
        }
    }
}
