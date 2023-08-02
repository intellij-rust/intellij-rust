/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.command

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.rust.cargo.runconfig.hasCargoProject

abstract class RunCargoCommandActionBase : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        val hasCargoProject = e.project?.hasCargoProject == true
        e.presentation.isEnabledAndVisible = hasCargoProject
    }
}
