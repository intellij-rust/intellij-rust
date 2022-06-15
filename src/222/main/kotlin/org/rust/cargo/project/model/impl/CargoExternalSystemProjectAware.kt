/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.*
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemSettingsFilesModificationContext.ReloadStatus
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.CargoProjectsService.CargoRefreshStatus
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.impl.CargoSettingsFilesService.SettingFileType

@Suppress("UnstableApiUsage")
class CargoExternalSystemProjectAware(
    private val project: Project
) : ExternalSystemProjectAware {

    override val projectId: ExternalSystemProjectId = ExternalSystemProjectId(CARGO_SYSTEM_ID, project.name)
    override val settingsFiles: Set<String>
        get() {
            val settingsFilesService = CargoSettingsFilesService.getInstance(project)
            // Always collect fresh settings files
            return settingsFilesService.collectSettingsFiles(useCache = false).keys
        }

    override fun isIgnoredSettingsFileEvent(path: String, context: ExternalSystemSettingsFilesModificationContext): Boolean {
        if (super.isIgnoredSettingsFileEvent(path, context)) return true
        // We consider any external change of `Cargo.lock` during project reloading is made by `Cargo`
        // and it shouldn't trigger new project reloading
        val fileName = PathUtil.getFileName(path)
        if (fileName == CargoConstants.LOCK_FILE &&
            context.modificationType == ExternalSystemModificationType.EXTERNAL &&
            (context.reloadStatus == ReloadStatus.IN_PROGRESS || context.reloadStatus == ReloadStatus.JUST_FINISHED)
        ) return true

        if (context.event != ExternalSystemSettingsFilesModificationContext.Event.UPDATE) return false

        // `isIgnoredSettingsFileEvent` is called just to filter settings files already detected by `settingsFiles` call,
        // so we don't need to collect fresh settings file list, and we can use cached value.
        // Also, `isIgnoredSettingsFileEvent` is called from EDT so using cache should make it much faster
        val settingsFiles = CargoSettingsFilesService.getInstance(project).collectSettingsFiles(useCache = true)
        return settingsFiles[path] == SettingFileType.IMPLICIT_TARGET
    }

    override fun reloadProject(context: ExternalSystemProjectReloadContext) {
        FileDocumentManager.getInstance().saveAllDocuments()
        project.cargoProjects.refreshAllProjects()
    }

    override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
        project.messageBus.connect(parentDisposable).subscribe(
            CargoProjectsService.CARGO_PROJECTS_REFRESH_TOPIC,
            object : CargoProjectsService.CargoProjectsRefreshListener {
                override fun onRefreshStarted() {
                    listener.onProjectReloadStart()
                }

                override fun onRefreshFinished(status: CargoRefreshStatus) {
                    val externalStatus = when (status) {
                        CargoRefreshStatus.SUCCESS -> ExternalSystemRefreshStatus.SUCCESS
                        CargoRefreshStatus.FAILURE -> ExternalSystemRefreshStatus.FAILURE
                        CargoRefreshStatus.CANCEL -> ExternalSystemRefreshStatus.CANCEL
                    }
                    listener.onProjectReloadFinish(externalStatus)
                }
            }
        )
    }

    companion object {
        val CARGO_SYSTEM_ID: ProjectSystemId = ProjectSystemId("Cargo")
    }
}
