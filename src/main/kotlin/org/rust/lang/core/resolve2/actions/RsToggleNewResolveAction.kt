/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.rust.ide.notifications.setStatusBarText
import org.rust.lang.core.resolve2.IS_NEW_RESOLVE_ENABLED_KEY

class RsToggleNewResolveAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val value = !IS_NEW_RESOLVE_ENABLED_KEY.asBoolean()
        IS_NEW_RESOLVE_ENABLED_KEY.setValue(value)
        project.setStatusBarText("New resolve is ${if (value) "enabled" else "disabled"}")
    }
}
