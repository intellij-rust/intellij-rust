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

/**
 * Subscribes to at most one message on the [topic].
 *
 * This function provides **zero guarantees** about which particular message will be delivered to the listener.
 * Don't use it for anything more important then notifications.
 *
 */
fun <L> subscribeForOneMessage(bus: MessageBus, topic: Topic<L>, listener: L) {
    val connection = bus.connect()
    connection.setDefaultHandler { method, args ->
        connection.disconnect()
        method.invoke(listener, *args)
    }
    connection.subscribe(topic)
}
