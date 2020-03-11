/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.isDispatchThread
import com.intellij.openapiext.isUnitTestMode
import com.intellij.ui.EditorNotificationPanel
import org.rust.cargo.project.model.*
import org.rust.lang.core.psi.isRustFile

class NoCargoProjectNotificationProvider(project: Project) : RsNotificationProvider(project) {

    init {
        project.messageBus.connect().apply {
            subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, object : CargoProjectsService.CargoProjectsListener {
                override fun cargoProjectsUpdated(projects: Collection<CargoProject>) {
                    updateAllNotifications()
                }
            })
        }
    }

    override val VirtualFile.disablingKey: String
        get() = NOTIFICATION_STATUS_KEY + path

    override fun createNotificationPanel(
        file: VirtualFile,
        editor: FileEditor,
        project: Project
    ): RsEditorNotificationPanel? {
        if (isUnitTestMode && !isDispatchThread) return null
        if (!(file.isRustFile || file.isCargoToml) || isNotificationDisabled(file)) return null

        val cargoProjects = project.cargoProjects
        if (!cargoProjects.hasAtLeastOneValidProject) {
            return createNoCargoProjectsPanel(file)
        }

        if (file.isCargoToml) {
            if (AttachCargoProjectAction.canBeAttached(project, file)) {
                return createNoCargoProjectForFilePanel(file)
            }
        } else if (cargoProjects.findProjectForFile(file) == null) {
            //TODO: more precise check here
            return createNoCargoProjectForFilePanel(file)
        }

        return null
    }

    private fun createNoCargoProjectsPanel(file: VirtualFile): RsEditorNotificationPanel =
        createAttachCargoProjectPanel(NO_CARGO_PROJECTS, file, "No Cargo projects found")

    private fun createNoCargoProjectForFilePanel(file: VirtualFile): RsEditorNotificationPanel =
        createAttachCargoProjectPanel(FILE_NOT_IN_CARGO_PROJECT, file, "File does not belong to any known Cargo project")

    private fun createAttachCargoProjectPanel(debugId: String, file: VirtualFile, message: String): RsEditorNotificationPanel =
        RsEditorNotificationPanel(debugId).apply {
            setText(message)
            createActionLabel("Attach", "Cargo.AttachCargoProject")
            createActionLabel("Do not show again") {
                disableNotification(file)
                updateAllNotifications()
            }
        }

    override fun getKey(): Key<EditorNotificationPanel> = PROVIDER_KEY

    companion object {
        private const val NOTIFICATION_STATUS_KEY = "org.rust.hideNoCargoProjectNotifications"

        const val NO_CARGO_PROJECTS = "NoCargoProjects"
        const val FILE_NOT_IN_CARGO_PROJECT = "FileNotInCargoProject"

        private val PROVIDER_KEY: Key<EditorNotificationPanel> = Key.create("No Cargo project")
    }
}
