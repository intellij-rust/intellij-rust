/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.ide.impl.isTrusted
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.rust.RsBundle
import org.rust.cargo.project.model.*
import org.rust.cargo.project.model.CargoProjectsService.CargoProjectsListener
import org.rust.cargo.project.settings.RsProjectSettingsServiceBase.*
import org.rust.cargo.project.settings.RsProjectSettingsServiceBase.Companion.RUST_SETTINGS_TOPIC
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.StandardLibrary
import org.rust.cargo.toolchain.tools.isRustupAvailable
import org.rust.lang.core.psi.isRustFile
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
            subscribe(RUST_SETTINGS_TOPIC, object : RsSettingsListener {
                override fun <T : RsProjectSettingsBase<T>> settingsChanged(e: SettingsChangedEventBase<T>) {
                    updateAllNotifications()
                }
            })

            subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, CargoProjectsListener { _, _ ->
                updateAllNotifications()
            })
        }
    }

    override fun createNotificationPanel(
        file: VirtualFile,
        editor: FileEditor,
        project: Project
    ): RsEditorNotificationPanel? {
        if (isUnitTestMode) return null
        if (!(file.isRustFile || file.isCargoToml) || isNotificationDisabled(file)) return null
        @Suppress("UnstableApiUsage")
        if (!project.isTrusted()) return null
        if (guessAndSetupRustProject(project)) return null

        val toolchain = project.toolchain
        if (toolchain == null || !toolchain.looksLikeValidToolchain()) {
            return createBadToolchainPanel(file)
        }

        val cargoProjects = project.cargoProjects

        if (!cargoProjects.initialized) return null

        val cargoProject = cargoProjects.findProjectForFile(file) ?: return null
        val workspace = cargoProject.workspace ?: return null
        if (!workspace.hasStandardLibrary) {
            // If rustup is not null, the WorkspaceService will use it
            // to add stdlib automatically. This happens asynchronously,
            // so we can't reliably say here if that succeeded or not.
            if (!toolchain.isRustupAvailable) return createLibraryAttachingPanel(project, file, cargoProject.rustcInfo)
        }

        return null
    }

    private fun createBadToolchainPanel(file: VirtualFile): RsEditorNotificationPanel =
        RsEditorNotificationPanel(NO_RUST_TOOLCHAIN).apply {
            text = RsBundle.message("notification.no.toolchain.configured")
            createActionLabel(RsBundle.message("notification.action.set.up.toolchain.text")) {
                project.rustSettings.configureToolchain()
            }
            createActionLabel(RsBundle.message("notification.action.do.not.show.again.text")) {
                disableNotification(file)
                updateAllNotifications()
            }
        }

    private fun createLibraryAttachingPanel(project: Project, file: VirtualFile, rustcInfo: RustcInfo?): RsEditorNotificationPanel =
        RsEditorNotificationPanel(NO_ATTACHED_STDLIB).apply {
            text = RsBundle.message("notification.can.not.attach.stdlib.sources")
            createActionLabel(RsBundle.message("notification.action.attach.manually.text")) {
                val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
                val stdlib = FileChooser.chooseFile(descriptor, this, this@MissingToolchainNotificationProvider.project, null) ?: return@createActionLabel
                if (StandardLibrary.fromFile(project, stdlib, rustcInfo) != null) {
                    this@MissingToolchainNotificationProvider.project.rustSettings.modify { it.explicitPathToStdlib = stdlib.path }
                } else {
                    this@MissingToolchainNotificationProvider.project.showBalloon(
                        RsBundle.message("notification.invalid.stdlib.source.path", stdlib.presentableUrl),
                        NotificationType.ERROR
                    )
                }
                updateAllNotifications()
            }

            createActionLabel(RsBundle.message("notification.action.do.not.show.again.text")) {
                disableNotification(file)
                updateAllNotifications()
            }
        }

    companion object {
        private const val NOTIFICATION_STATUS_KEY = "org.rust.hideToolchainNotifications"
        const val NO_RUST_TOOLCHAIN = "NoRustToolchain"
        const val NO_ATTACHED_STDLIB = "NoAttachedStdlib"
    }
}
