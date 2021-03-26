/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager

object RsNotifications {

    fun buildLogGroup(): NotificationGroup {
        return NotificationGroupManager.getInstance().getNotificationGroup("Rust Build Log")
    }

    fun pluginNotifications(): NotificationGroup {
        return NotificationGroupManager.getInstance().getNotificationGroup("Rust Plugin")
    }
}
