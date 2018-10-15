/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.actionSystem.AnActionEvent
import org.rust.cargo.project.toolwindow.CargoToolWindow

class DetachCargoProjectAction : CargoProjectActionBase() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null && e.cargoProject != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val cargoProject = e.cargoProject ?: return
        project.cargoProjects.detachCargoProject(cargoProject)
    }

    private val AnActionEvent.cargoProject get() = getData(CargoToolWindow.SELECTED_CARGO_PROJECT)

}
