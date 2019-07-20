/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import org.rust.cargo.project.settings.rustSettings

class ToggleExternalLinterOnTheFlyAction : ToggleAction() {

    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        return project.rustSettings.runExternalLinterOnTheFly
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        project.rustSettings.modify { it.runExternalLinterOnTheFly = state }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val externalLinterName = e.project?.rustSettings?.externalLinter?.title ?: "External Linter"
        e.presentation.text = "Run $externalLinterName on the Fly"
    }
}
