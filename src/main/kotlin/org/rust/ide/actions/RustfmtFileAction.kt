/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RustToolchain
import org.rust.ide.notifications.showBalloon
import org.rust.lang.core.psi.isRustFile

class RustfmtFileAction : DumbAwareAction() {
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = getContext(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val (project, toolchain, file) = getContext(e) ?: return

        val cargo = toolchain.rawCargo()
        try {
            cargo.reformatFile(project, file)
        } catch (e: ExecutionException) {
            val message = e.message ?: ""

            // #1131 - Check if we get a `no such subcommand: fmt` and let the user know to install fmt
            if ("no such subcommand: fmt" in message) {
                project.showBalloon("Install rustfmt: https://github.com/rust-lang-nursery/rustfmt", NotificationType.ERROR)
            } else {
                project.showBalloon(message, NotificationType.ERROR)
            }
        }
    }

    private fun getContext(e: AnActionEvent): Triple<Project, RustToolchain, VirtualFile>? {
        val project = e.project ?: return null
        val toolchain = project.toolchain ?: return null
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        if (!(file.isInLocalFileSystem && file.isRustFile)) return null
        return Triple(project, toolchain, file)
    }
}
