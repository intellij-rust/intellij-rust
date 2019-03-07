/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.lang.core.psi.isNotRustFile

class EnableExternalLinterNotificationProvider(
    private val project: Project,
    private val notifications: EditorNotifications
) : EditorNotifications.Provider<EditorNotificationPanel>(), DumbAware {

    override fun getKey(): Key<EditorNotificationPanel> = PROVIDER_KEY

    override fun createNotificationPanel(file: VirtualFile, editor: FileEditor): EditorNotificationPanel? {
        if (file.isNotRustFile || isNotificationDisabled() || project.rustSettings.runExternalLinterOnTheFly) {
            return null
        }

        return EditorNotificationPanel().apply {
            setText("External linter is disabled")
            for (externalLinter in ExternalLinter.values()) {
                createActionLabel("Use ${externalLinter.title}") {
                    project.rustSettings.modify {
                        it.externalLinter = externalLinter
                        it.runExternalLinterOnTheFly = true
                    }
                }
            }
            createActionLabel("Do not show again") {
                disableNotification()
                notifications.updateAllNotifications()
            }
        }
    }

    private fun disableNotification() =
        PropertiesComponent.getInstance().setValue(NOTIFICATION_STATUS_KEY, true)

    private fun isNotificationDisabled(): Boolean =
        PropertiesComponent.getInstance().getBoolean(NOTIFICATION_STATUS_KEY)

    companion object {
        @JvmField
        val PROVIDER_KEY: Key<EditorNotificationPanel> = Key.create("Enable External Linter on the fly")
        private const val NOTIFICATION_STATUS_KEY = "org.rust.hideExternalLinterNotifications"
    }
}
