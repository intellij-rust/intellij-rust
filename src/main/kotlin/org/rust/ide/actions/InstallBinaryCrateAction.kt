/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.rust.cargo.project.settings.toolchain

class InstallBinaryCrateAction(private val crateName: String) : DumbAwareAction("Install") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val cargo = project.toolchain?.rawCargo() ?: return
        Notification.get(e).expire()
        cargo.installBinaryCrate(project, crateName)
    }
}
