/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.confirmLoadingUntrustedProject
import com.intellij.ide.impl.isTrusted
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.openapi.wm.WindowManager
import org.rust.RsBundle

fun Project.showBalloon(
    @Suppress("UnstableApiUsage") @NotificationContent content: String,
    type: NotificationType,
    action: AnAction? = null
) {
    showBalloon("", content, type, action)
}

fun Project.showBalloon(
    @Suppress("UnstableApiUsage") @NotificationTitle title: String,
    @Suppress("UnstableApiUsage") @NotificationContent content: String,
    type: NotificationType,
    action: AnAction? = null,
    listener: NotificationListener? = null
) {
    val notification = RsNotifications.pluginNotifications().createNotification(title, content, type, listener)
    if (action != null) {
        notification.addAction(action)
    }
    Notifications.Bus.notify(notification, this)
}

fun showBalloonWithoutProject(@Suppress("UnstableApiUsage") @NotificationContent content: String, type: NotificationType) {
    val notification = RsNotifications.pluginNotifications().createNotification(content, type)
    Notifications.Bus.notify(notification)
}

fun Project.setStatusBarText(@Suppress("UnstableApiUsage") @StatusBarText text: String) {
    val statusBar = WindowManager.getInstance().getStatusBar(this)
    statusBar?.info = text
}

@Suppress("UnstableApiUsage")
fun Project.confirmLoadingUntrustedProject(): Boolean {
    return isTrusted() || confirmLoadingUntrustedProject(
        this,
        title = IdeBundle.message("untrusted.project.dialog.title", RsBundle.message("cargo"), 1),
        message = IdeBundle.message("untrusted.project.dialog.text", RsBundle.message("cargo"), 1),
        trustButtonText = IdeBundle.message("untrusted.project.dialog.trust.button"),
        distrustButtonText = IdeBundle.message("untrusted.project.dialog.distrust.button")
    )
}
