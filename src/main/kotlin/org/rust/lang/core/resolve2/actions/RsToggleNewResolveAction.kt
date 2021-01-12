/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.rust.cargo.project.settings.rustSettings
import org.rust.ide.notifications.setStatusBarText

class RsToggleNewResolveAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.rustSettings.modify {
            val value = !it.newResolveEnabled
            it.newResolveEnabled = value
            project.setStatusBarText("New resolve is ${if (value) "enabled" else "disabled"}")
        }
    }
}
