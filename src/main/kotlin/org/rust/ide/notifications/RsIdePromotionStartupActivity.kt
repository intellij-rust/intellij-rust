/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.PlatformUtils
import org.rust.RsBundle
import org.rust.cargo.runconfig.hasCargoProject

private const val DO_NOT_SHOW_KEY: String = "com.jetbrains.rust.ide.promotion"

class RsIdePromotionStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (PropertiesComponent.getInstance().getBoolean(DO_NOT_SHOW_KEY, false)) return
        if (ApplicationManager.getApplication().isUnitTestMode || !project.hasCargoProject) return
        if (PlatformUtils.isIdeaUltimate() && ApplicationInfo.getInstance().build.baselineVersion >= 241) {
            return
        }
        val notification = NotificationGroupManager.getInstance().getNotificationGroup("Rust Plugin Promotion").createNotification(RsBundle.message("notification.title.introducing.rustrover.dedicated.rust.ide.by.jetbrains"), RsBundle.message("notification.content.rust.plugin.no.longer.officialy.supporter"), NotificationType.INFORMATION)
        notification.addAction(object : AnAction(RsBundle.message("action.download.rustrover.text")) {
            override fun actionPerformed(p0: AnActionEvent) {
                notification.expire()
                BrowserUtil.browse("https://www.jetbrains.com/rustrover/download")
            }
        })
        notification.addAction(object : AnAction(RsBundle.message("don.t.show.again")) {
            override fun actionPerformed(p0: AnActionEvent) {
                PropertiesComponent.getInstance().setValue(DO_NOT_SHOW_KEY, true)
                notification.expire()
            }
        })
        Notifications.Bus.notify(notification, project)

    }
}
