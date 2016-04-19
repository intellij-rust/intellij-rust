package org.rust.cargo

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.containers.isNullOrEmpty
import org.rust.cargo.projectSettings.toolchain
import org.rust.cargo.toolchain.CargoMetadataService
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.getModules
import org.rust.cargo.util.getService

class RefreshCargoProjectAction : AnAction() {
    init {
        templatePresentation.text = "Refresh Cargo project"
        templatePresentation.description = "Update Cargo project information and download new dependencies"
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
            val service = module.getService<CargoMetadataService>()
            service.updateNow(toolchain)
        }
    }
}

private val Project.modulesWithToolchains: List<Pair<Module, RustToolchain>>
    get() = getModules().orEmpty().mapNotNull { module ->
        module.toolchain?.let { Pair(module, it) }
    }
