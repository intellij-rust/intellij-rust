/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications

abstract class RsNotificationProvider(
    protected val project: Project
) : EditorNotifications.Provider<EditorNotificationPanel>() {

    protected abstract val VirtualFile.disablingKey: String

    abstract override fun createNotificationPanel(
        file: VirtualFile,
        editor: FileEditor,
        project: Project
    ): RsEditorNotificationPanel?

    protected fun updateAllNotifications() {
        EditorNotifications.getInstance(project).updateAllNotifications()
    }

    protected fun disableNotification(file: VirtualFile) {
        PropertiesComponent.getInstance(project).setValue(file.disablingKey, true)
    }

    protected fun isNotificationDisabled(file: VirtualFile): Boolean =
        PropertiesComponent.getInstance(project).getBoolean(file.disablingKey)
}

class RsEditorNotificationPanel(val debugId: String) : EditorNotificationPanel() {
    override fun getActionPlace(): String = NOTIFICATION_PANEL_PLACE

    companion object {
        const val NOTIFICATION_PANEL_PLACE = "RsEditorNotificationPanel"
    }
}
