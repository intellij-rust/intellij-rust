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
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import org.rust.RsBundle
import java.awt.Component
import java.awt.Point
import javax.swing.event.HyperlinkListener

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

fun Component.showBalloon(
    @Suppress("UnstableApiUsage") @NotificationContent content: String,
    type: MessageType,
    disposable: Disposable = ApplicationManager.getApplication(),
    listener: HyperlinkListener? = null
) {
    val popupFactory = JBPopupFactory.getInstance() ?: return
    val balloon = popupFactory.createHtmlTextBalloonBuilder(content, type, listener)
        .setShadow(false)
        .setAnimationCycle(200)
        .setHideOnLinkClick(true)
        .setDisposable(disposable)
        .createBalloon()
    balloon.setAnimationEnabled(false)
    val x: Int
    val y: Int
    val position: Balloon.Position
    if (size == null) {
        y = 0
        x = y
        position = Balloon.Position.above
    } else {
        x = size.width / 2
        y = 0
        position = Balloon.Position.above
    }
    balloon.show(RelativePoint(this, Point(x, y)), position)
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
