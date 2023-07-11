/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import org.rust.RsBundle
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.tools.rustup
import org.rust.cargo.util.DownloadResult
import org.rust.ide.notifications.showBalloon
import java.nio.file.Path

class InstallComponentAction(
    private val projectDirectory: Path,
    private val componentName: String
) : DumbAwareAction(RsBundle.message("action.install.text")) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val rustup = project.toolchain?.rustup(projectDirectory) ?: return
        Notification.get(e).expire()
        object : Task.Backgroundable(project, RsBundle.message("progress.title.installing2", componentName)) {
            override fun shouldStartInBackground(): Boolean = false
            override fun run(indicator: ProgressIndicator) {
                val result = rustup.downloadComponent(project, componentName) as? DownloadResult.Err ?: return
                project.showBalloon(result.error, NotificationType.ERROR)
            }
        }.queue()
    }
}
