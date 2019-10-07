/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.notification.NotificationType
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.guessAndSetupRustProject
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.StandardLibrary
import org.rust.lang.core.psi.isNotRustFile
import org.rust.openapiext.isUnitTestMode

/**
 * Warn user if rust toolchain or standard library is not properly configured.
 *
 * Try to fix this automatically (toolchain from PATH, standard library from the last project)
 * and if not successful show the actual notification to the user.
 */
class MissingToolchainNotificationProvider(project: Project) : RsNotificationProvider(project), DumbAware {

    override val VirtualFile.disablingKey: String get() = NOTIFICATION_STATUS_KEY

    init {
        project.messageBus.connect().apply {
            subscribe(RustProjectSettingsService.TOOLCHAIN_TOPIC,
                object : RustProjectSettingsService.ToolchainListener {
                    override fun toolchainChanged() {
                        updateAllNotifications()
                    }
                })

            subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, object : CargoProjectsService.CargoProjectsListener {
                override fun cargoProjectsUpdated(projects: Collection<CargoProject>) {
                    updateAllNotifications()
                }
            })
        }
    }

    override fun getKey(): Key<EditorNotificationPanel> = PROVIDER_KEY

    override fun createNotificationPanel(
        file: VirtualFile,
        editor: FileEditor,
        project: Project
    ): RsEditorNotificationPanel? {
        if (isUnitTestMode) return null
        if (file.isNotRustFile || isNotificationDisabled(file)) return null
        if (guessAndSetupRustProject(project)) return null

        val toolchain = project.toolchain
        if (toolchain == null || !toolchain.looksLikeValidToolchain()) {
            return createBadToolchainPanel(file)
        }

        val cargoProjects = project.cargoProjects
        if (!cargoProjects.hasAtLeastOneValidProject) {
            return createNoCargoProjectsPanel(file)
        }

        val cargoProject = cargoProjects.findProjectForFile(file) ?:
            //TODO: more precise check here
            return createNoCargoProjectForFilePanel(file)

        val workspace = cargoProject.workspace ?: return null
        if (!workspace.hasStandardLibrary) {
            // If rustup is not null, the WorkspaceService will use it
            // to add stdlib automatically. This happens asynchronously,
            // so we can't reliably say here if that succeeded or not.
            if (!toolchain.isRustupAvailable) return createLibraryAttachingPanel(file)
        }

        return null
    }

    private fun createBadToolchainPanel(file: VirtualFile): RsEditorNotificationPanel =
        RsEditorNotificationPanel(NO_RUST_TOOLCHAIN).apply {
            setText("No Rust toolchain configured")
            createActionLabel("Setup toolchain") {
                project.rustSettings.configureToolchain()
            }
            createActionLabel("Do not show again") {
                disableNotification(file)
                updateAllNotifications()
            }
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

    private fun createLibraryAttachingPanel(file: VirtualFile): RsEditorNotificationPanel =
        RsEditorNotificationPanel(NO_ATTACHED_STDLIB).apply {
            setText("Can not attach stdlib sources automatically without rustup.")
            createActionLabel("Attach manually") {
                val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                val stdlib = FileChooser.chooseFile(descriptor, this, project, null) ?: return@createActionLabel
                if (StandardLibrary.fromFile(stdlib) != null) {
                    project.rustSettings.modify { it.explicitPathToStdlib = stdlib.path }
                } else {
                    project.showBalloon(
                        "Invalid Rust standard library source path: `${stdlib.presentableUrl}`",
                        NotificationType.ERROR
                    )
                }
                updateAllNotifications()
            }

            createActionLabel("Do not show again") {
                disableNotification(file)
                updateAllNotifications()
            }
        }

    companion object {
        private const val NOTIFICATION_STATUS_KEY = "org.rust.hideToolchainNotifications"
        const val NO_RUST_TOOLCHAIN = "NoRustToolchain"
        const val NO_CARGO_PROJECTS = "NoCargoProjects"
        const val FILE_NOT_IN_CARGO_PROJECT = "FileNotInCargoProject"
        const val NO_ATTACHED_STDLIB = "NoAttachedStdlib"

        private val PROVIDER_KEY: Key<EditorNotificationPanel> = Key.create("Setup Rust toolchain")
    }
}
