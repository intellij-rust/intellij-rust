/*
* Use of this source code is governed by the MIT license that can be
* found in the LICENSE file.
*/

package org.rust.cargo.runconfig.command

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.toolwindow.CargoToolWindow
import org.rust.cargo.runconfig.hasCargoProject
import javax.swing.Icon

abstract class RunCargoCommandActionBase(icon: Icon) : AnAction(icon) {
    override fun update(e: AnActionEvent) {
        val hasCargoProject = e.project?.hasCargoProject == true
        e.presentation.isEnabledAndVisible = hasCargoProject
    }

    protected fun getAppropriateCargoProject(e: AnActionEvent): CargoProject? {
        val cargoProjects = e.project?.cargoProjects ?: return null
        cargoProjects.allProjects.singleOrNull()?.let { return it }

        e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?.let { cargoProjects.findProjectForFile(it) }
            ?.let { return it }

        return e.getData(CargoToolWindow.SELECTED_CARGO_PROJECT)
            ?: cargoProjects.allProjects.firstOrNull()
    }
}
