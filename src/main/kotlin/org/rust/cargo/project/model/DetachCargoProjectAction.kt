/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey

class DetachCargoProjectAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null && e.cargoProject != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val cargoProject = e.cargoProject ?: return
        project.cargoProjects.detachCargoProject(cargoProject)
    }

    private val AnActionEvent.cargoProject get() = dataContext.getData(CARGO_PROJECT_TO_DETACH)

    companion object {
        val CARGO_PROJECT_TO_DETACH: DataKey<CargoProject> =
            DataKey.create<CargoProject>("CARGO_PROJECT_TO_DETACH")
    }
}
