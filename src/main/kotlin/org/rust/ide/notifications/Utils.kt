/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project

private val PLUGIN_NOTIFICATIONS: NotificationGroup =
    NotificationGroup.balloonGroup("Rust Plugin")

fun Project.showBalloon(content: String, type: NotificationType, action: AnAction? = null) {
    val notification = PLUGIN_NOTIFICATIONS.createNotification(content, type)
    if (action != null) {
        notification.addAction(action)
    }
    Notifications.Bus.notify(notification, this)
}

fun showBalloonWithoutProject(content: String, type: NotificationType) {
    val notification = PLUGIN_NOTIFICATIONS.createNotification(content, type)
    Notifications.Bus.notify(notification)
}
