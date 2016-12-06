package org.rust.ide.notifications

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic

private val pluginNotifications = NotificationGroup.balloonGroup("Rust plugin notifications")

fun Project.showBalloon(content: String, type: NotificationType) {
    val notification = pluginNotifications.createNotification(content, type)
    Notifications.Bus.notify(notification, this)
}
