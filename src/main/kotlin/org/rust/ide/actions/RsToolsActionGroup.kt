/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import org.rust.cargo.runconfig.hasCargoProject

class RsToolsActionGroup : DefaultActionGroup(), DumbAware {

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        e.presentation.isEnabledAndVisible = project.hasCargoProject
    }
}
