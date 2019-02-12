/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.rust.cargo.project.settings.rustSettings

class ToggleCargoCheckAction : AnAction() {

    companion object {
        private const val disableText = "Disable Cargo Check"
        private const val enableText = "Enable Cargo Check"
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val isHintsShownNow = project.rustSettings.useCargoCheckAnnotator
        e.presentation.text = if (isHintsShownNow) disableText else enableText
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = project.rustSettings
        val before = settings.useCargoCheckAnnotator
        settings.data = settings.data.copy(useCargoCheckAnnotator = !before)
    }
}
