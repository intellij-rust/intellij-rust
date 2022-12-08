/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.ide.impl.isTrusted
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.LinkLabel
import com.intellij.openapi.vfs.VirtualFile
import org.rust.RsBundle
import org.rust.cargo.project.model.AttachCargoProjectAction
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.CargoProjectsService.CargoProjectsListener
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.isCargoToml
import org.rust.lang.core.psi.isRustFile
import org.rust.openapiext.isDispatchThread
import org.rust.openapiext.isUnitTestMode

class NoCargoProjectNotificationProvider(project: Project) : RsNotificationProvider(project) {

    init {
        project.messageBus.connect().apply {
            subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, CargoProjectsListener { _, _ ->
                updateAllNotifications()
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
        if (ScratchUtil.isScratch(file)) return null
        @Suppress("UnstableApiUsage")
        if (!project.isTrusted()) return null

        val cargoProjects = project.cargoProjects
        if (!cargoProjects.initialized) return null
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
        createAttachCargoProjectPanel(NO_CARGO_PROJECTS, file, RsBundle.message("notification.no.cargo.projects.found"))

    private fun createNoCargoProjectForFilePanel(file: VirtualFile): RsEditorNotificationPanel =
        createAttachCargoProjectPanel(FILE_NOT_IN_CARGO_PROJECT, file, RsBundle.message("notification.file.not.belong.to.cargo.project"))

    @Suppress("UnstableApiUsage")
    private fun createAttachCargoProjectPanel(debugId: String, file: VirtualFile, @LinkLabel message: String): RsEditorNotificationPanel =
        RsEditorNotificationPanel(debugId).apply {
            text = message
            createActionLabel(RsBundle.message("notification.action.attach.text"), "Cargo.AttachCargoProject")
            createActionLabel(RsBundle.message("notification.action.do.not.show.again.text")) {
                disableNotification(file)
                updateAllNotifications()
            }
        }

    companion object {
        private const val NOTIFICATION_STATUS_KEY = "org.rust.hideNoCargoProjectNotifications"

        const val NO_CARGO_PROJECTS = "NoCargoProjects"
        const val FILE_NOT_IN_CARGO_PROJECT = "FileNotInCargoProject"
    }
}
