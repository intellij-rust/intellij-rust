/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.rust.cargo.project.toolwindow.CargoToolWindow

class DetachCargoProjectAction : AnAction() {
    private val AnActionEvent.cargoProject: CargoProject?
        get() = getData(CargoToolWindow.SELECTED_CARGO_PROJECT)

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null && event.cargoProject != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val cargoProject = event.cargoProject ?: return
        project.cargoProjects.detachCargoProject(cargoProject)
    }
}
