/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import org.rust.RsBundle
import org.rust.cargo.project.settings.externalLinterSettings

class ToggleExternalLinterOnTheFlyAction : ToggleAction() {

    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        return project.externalLinterSettings.runOnTheFly
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        project.externalLinterSettings.modify { it.runOnTheFly = state }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        val externalLinterName = e.project?.externalLinterSettings?.tool?.title ?: "External Linter"
        e.presentation.text = RsBundle.message("action.run.on.fly.text", externalLinterName)
    }
}
