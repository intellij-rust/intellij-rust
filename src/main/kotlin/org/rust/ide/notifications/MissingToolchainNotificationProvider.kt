package org.rust.ide.notifications

import com.intellij.ProjectTopics
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.project.workspace.StandardLibrary
import org.rust.cargo.util.cargoProjectRoot
import org.rust.lang.core.psi.isNotRustFile
import java.awt.Component

/**
 * Warn user if rust toolchain or standard library is not properly configured.
 *
 * Try to fix this automatically (toolchain from PATH, standard library from the last project)
 * and if not successful show the actual notification to the user.
 */
class MissingToolchainNotificationProvider(
    private val project: Project,
    private val notifications: EditorNotifications
) : EditorNotifications.Provider<EditorNotificationPanel>() {

    init {
        project.messageBus.connect(project).apply {
            subscribe(RustProjectSettingsService.TOOLCHAIN_TOPIC,
                object : RustProjectSettingsService.ToolchainListener {
                    override fun toolchainChanged() {
                        notifications.updateAllNotifications()
                    }
                })

            subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
                override fun beforeRootsChange(event: ModuleRootEvent?) {
                    // NOP
                }

                override fun rootsChanged(event: ModuleRootEvent?) {
                    notifications.updateAllNotifications()
                }
            })
        }
    }

    override fun getKey(): Key<EditorNotificationPanel> = PROVIDER_KEY

    override fun createNotificationPanel(file: VirtualFile, editor: FileEditor): EditorNotificationPanel? {
        if (file.isNotRustFile || isNotificationDisabled()) return null

        val toolchain = project.toolchain
        if (toolchain == null || !toolchain.looksLikeValidToolchain()) {
            return if (trySetupToolchainAutomatically())
                null
            else
                createBadToolchainPanel()
        }

        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
        if (module.cargoWorkspace?.hasStandardLibrary == false) {
            val rustup = module.cargoProjectRoot?.let { toolchain.rustup(it.path) }
            // If rustup is not null, the WorkspaceService will use it
            // to add stdlib automatically. This happens asynchronously,
            // so we can't reliably say here if that succeeded or not.
            if (rustup == null) {
                createLibraryAttachingPanel(module)
            }
        }

        return null
    }

    private fun trySetupToolchainAutomatically(): Boolean {
        if (alreadyTriedForThisProject(TOOLCHAIN_DISCOVERY_KEY)) return false

        val toolchain = RustToolchain.suggest() ?: return false

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val oldToolchain = project.rustSettings.toolchain
            if (oldToolchain != null && oldToolchain.looksLikeValidToolchain()) {
                return@invokeLater
            }

            runWriteAction {
                project.rustSettings.toolchain = toolchain
            }

            project.showBalloon("Using Cargo at ${toolchain.presentableLocation}", NotificationType.INFORMATION)
            notifications.updateAllNotifications()
        }

        return true
    }

    private fun createBadToolchainPanel(): EditorNotificationPanel =
        EditorNotificationPanel().apply {
            setText("No Rust toolchain configured")
            createActionLabel("Setup toolchain") {
                project.rustSettings.configureToolchain()
            }
            createActionLabel("Do not show again") {
                disableNotification()
                notifications.updateAllNotifications()
            }
        }


    private fun createLibraryAttachingPanel(module: Module): EditorNotificationPanel =
        EditorNotificationPanel().apply {
            setText("Can not attach stdlib sources automatically without rustup.")
            createActionLabel("Attach manually") {
                val stdlib = chooseStdlibLocation(this) ?: return@createActionLabel
                if (!tryAttachStdlibToModule(module, stdlib)) {
                    project.showBalloon(
                        "Invalid Rust standard library source path: `${stdlib.presentableUrl}`",
                        NotificationType.ERROR
                    )
                }
                notifications.updateAllNotifications()
            }

            createActionLabel("Do not show again") {
                disableNotification()
                notifications.updateAllNotifications()
            }
        }

    private fun chooseStdlibLocation(parent: Component): VirtualFile? {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()

        return FileChooser.chooseFile(descriptor, parent, project, null)
    }

    private fun tryAttachStdlibToModule(module: Module, stdlib: VirtualFile): Boolean {
        val roots = StandardLibrary.fromFile(stdlib)
            ?: return false

        runWriteAction { roots.attachTo(module) }
        return true
    }

    private fun disableNotification() {
        PropertiesComponent.getInstance(project).setValue(NOTIFICATION_STATUS_KEY, true)
    }

    private fun isNotificationDisabled(): Boolean =
        PropertiesComponent.getInstance(project).getBoolean(NOTIFICATION_STATUS_KEY)

    private fun alreadyTriedForThisProject(key: String): Boolean {
        val properties = PropertiesComponent.getInstance(project)
        val result = properties.getBoolean(key)
        properties.setValue(key, true)

        return result
    }

    companion object {
        private val NOTIFICATION_STATUS_KEY = "org.rust.hideToolchainNotifications"
        private val TOOLCHAIN_DISCOVERY_KEY = "org.rust.alreadyTriedToolchainAutoDiscovery"

        private val PROVIDER_KEY: Key<EditorNotificationPanel> = Key.create("Setup Rust toolchain")
    }
}
