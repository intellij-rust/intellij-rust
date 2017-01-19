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
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.cargo.util.StandardLibraryRoots
import org.rust.cargo.util.cargoProjectRoot
import org.rust.ide.utils.service
import org.rust.lang.core.psi.impl.isNotRustFile
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
                    override fun toolchainChanged(newToolchain: RustToolchain?) {
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
            return if (trySetupLibraryAutomatically(module, rustup)) {
                null
            } else {
                createLibraryAttachingPanel(module, rustup)
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
                project.service<RustProjectSettingsService>().configureToolchain()
            }
            createActionLabel("Do not show again") {
                disableNotification()
                notifications.updateAllNotifications()
            }
        }


    private fun trySetupLibraryAutomatically(module: Module, rustup: Rustup?): Boolean {
        if (alreadyTriedForThisProject(LIBRARY_DISCOVERY_KEY)) return false

        val stdlib = rustup?.getStdlibFromSysroot()
            ?:
            PropertiesComponent.getInstance().getValue(LIBRARY_LOCATION_KEY)?.let { previousLocation ->
                VirtualFileManager.getInstance().findFileByUrl(previousLocation)
            }
            ?: return false

        ApplicationManager.getApplication().invokeLater {
            if (module.isDisposed) return@invokeLater
            if (tryAttachStdlibToModule(module, stdlib)) {
                project.showBalloon(
                    "Using rust standard library at ${stdlib.presentableUrl}",
                    NotificationType.INFORMATION
                )
            }
            notifications.updateAllNotifications()
        }

        return true
    }

    private fun createLibraryAttachingPanel(module: Module, rustup: Rustup?): EditorNotificationPanel =
        EditorNotificationPanel().apply {
            setText("No standard library sources found, some code insight will not work")

            if (rustup != null) {
                createActionLabel("Download via rustup") {
                    object : Task.Backgroundable(module.project, "rustup component add rust-src") {
                        private lateinit var result: Rustup.DownloadResult

                        override fun run(indicator: ProgressIndicator) {
                            result = rustup.downloadStdlib()
                        }

                        override fun onSuccess() {
                            if (module.isDisposed) return
                            val result = result
                            when (result) {
                                is Rustup.DownloadResult.Ok -> tryAttachStdlibToModule(module, result.library)
                                is Rustup.DownloadResult.Err ->
                                    project.showBalloon(
                                        "Failed to download standard library: ${result.error}",
                                        NotificationType.ERROR
                                    )
                            }

                            notifications.updateAllNotifications()
                        }
                    }.queue()
                }
            } else {
                createActionLabel("Attach") {
                    val stdlib = chooseStdlibLocation(this) ?: return@createActionLabel
                    if (!tryAttachStdlibToModule(module, stdlib)) {
                        project.showBalloon(
                            "Invalid Rust standard library source path: `${stdlib.presentableUrl}`",
                            NotificationType.ERROR
                        )
                    }
                    notifications.updateAllNotifications()
                }
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
        val roots = StandardLibraryRoots.fromFile(stdlib)
            ?: return false

        runWriteAction { roots.attachTo(module) }
        PropertiesComponent.getInstance().setValue(LIBRARY_LOCATION_KEY, stdlib.url)
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
        private val LIBRARY_DISCOVERY_KEY = "org.rust.alreadyTriedLibraryAutoDiscovery"
        private val LIBRARY_LOCATION_KEY = "org.rust.previousLibraryLocation"

        private val PROVIDER_KEY: Key<EditorNotificationPanel> = Key.create("Setup Rust toolchain")
    }
}
