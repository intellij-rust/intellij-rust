/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.guessAndSetupRustProject
import org.rust.cargo.project.settings.toolchain

class RefreshCargoProjectAction : AnAction() {
    init {
        templatePresentation.text = "Refresh Cargo project"
        templatePresentation.description = "Update Cargo project information and download new dependencies"
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || project.toolchain == null || project.cargoProjects.allProjects.isEmpty()) {
            e.presentation.isEnabled = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (project.toolchain == null || project.cargoProjects.allProjects.isEmpty()) {
            guessAndSetupRustProject(project, explicitRequest = true)
        } else {
            project.cargoProjects.refreshAllProjects()
        }
    }
}
