/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager

// BACKCOMPAT: 2020.2
@Suppress("DEPRECATION")
private val pluginNotifications = NotificationGroup.balloonGroup("Rust Plugin")

fun Project.showBalloon(content: String, type: NotificationType, action: AnAction? = null) {
    showBalloon("", content, type, action)
}

fun Project.showBalloon(
    title: String,
    content: String,
    type: NotificationType,
    action: AnAction? = null,
    listener: NotificationListener? = null
) {
    val notification = pluginNotifications.createNotification(title, content, type, listener)
    if (action != null) {
        notification.addAction(action)
    }
    Notifications.Bus.notify(notification, this)
}

fun showBalloonWithoutProject(content: String, type: NotificationType) {
    val notification = pluginNotifications.createNotification(content, type)
    Notifications.Bus.notify(notification)
}

fun Project.setStatusBarText(text: String) {
    val statusBar = WindowManager.getInstance().getStatusBar(this)
    statusBar?.info = text
}
