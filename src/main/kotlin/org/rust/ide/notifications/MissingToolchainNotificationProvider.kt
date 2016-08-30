package org.rust.ide.notifications

import com.intellij.ProjectTopics
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.RustcVersion
import org.rust.cargo.util.StandardLibraryRoots
import org.rust.cargo.util.cargoProject
import org.rust.ide.utils.runWriteAction
import org.rust.ide.utils.service
import org.rust.lang.core.psi.impl.isNotRustFile
import org.rust.utils.download
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
        if (file.isNotRustFile || isNotificationDisabled())
            return null

        val toolchain = project.toolchain
        if (toolchain == null || !toolchain.looksLikeValidToolchain()) {
            return if (trySetupToolchainAutomatically())
                null
            else
                createBadToolchainPanel("No Rust toolchain configured")
        }

        val versionInfo = toolchain.queryVersions()

        if (!versionInfo.cargoHasMetadataCommand) {
            return createBadToolchainPanel("Configured Rust toolchain is incompatible with the plugin: " +
                "required at least Cargo ${RustToolchain.CARGO_LEAST_COMPATIBLE_VERSION}, " +
                "found ${versionInfo.cargo}")
        }

        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
        if (module.cargoProject?.hasStandardLibrary == false) {
            return if (trySetupLibraryAutomatically(module)) {
                null
            } else {
                createLibraryAttachingPanel(module, toolchain)
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

    private fun trySetupLibraryAutomatically(module: Module): Boolean {
        if (alreadyTriedForThisProject(LIBRARY_DISCOVERY_KEY)) return false

        val previousLocation = PropertiesComponent.getInstance().getValue(LIBRARY_LOCATION_KEY) ?: return false
        val stdlib = VirtualFileManager.getInstance().findFileByUrl(previousLocation) ?: return false

        ApplicationManager.getApplication().invokeLater {
            if (module.isDisposed) return@invokeLater
            if (tryAttachStdlibToModule(module, stdlib)) {
                project.showBalloon("Using rust standard library at ${stdlib.presentableUrl}", NotificationType.INFORMATION)
            }
            notifications.updateAllNotifications()
        }

        return true
    }

    private fun createBadToolchainPanel(title: String): EditorNotificationPanel =
        EditorNotificationPanel().apply {
            setText(title)
            createActionLabel("Setup toolchain") {
                project.service<RustProjectSettingsService>().configureToolchain()
            }
            createActionLabel("Do not show again") {
                disableNotification()
                notifications.updateAllNotifications()
            }
        }

    private fun createLibraryAttachingPanel(module: Module, toolchain: RustToolchain): EditorNotificationPanel =
        EditorNotificationPanel().apply {
            setText("No standard library sources found, some code insight will not work")

            createActionLabel("Download") {
                val destination = chooseDownloadLocation(this) ?: return@createActionLabel

                val task = object: Task.Backgroundable(module.project, "stdlib download") {

                    private var library: VirtualFile? = null

                    override fun run(indicator: ProgressIndicator) {
                        val version = toolchain.queryVersions().rustc
                        if (version == null) {
                            LOG.warn("Failed to query rustc version for downloading standard library with $toolchain")
                            return
                        }
                        val url = sourcesArchiveUrlFromVersion(version)
                        library = download(url, "rust-${version.semver.parsedVersion}-src.zip", destination)
                    }

                    private fun sourcesArchiveUrlFromVersion(v: RustcVersion): String {
                        // We download sources from github and not from rust-lang.org, because we want zip archives.
                        // rust-lang.org hosts only .tar.gz.
                        val tag = v.nightlyCommitHash ?: v.semver.parsedVersion
                        return "https://github.com/rust-lang/rust/archive/$tag.zip"
                    }

                    override fun onSuccess() {
                        if (module.isDisposed)
                            return

                        if (!tryAttachStdlibToModule(module, library!!)) {
                            showWrongStdlibBalloon(library!!)
                        }
                        notifications.updateAllNotifications()
                    }
                }

                task.queue()
            }

            createActionLabel("Attach") {
                val stdlib = chooseStdlibLocation(this) ?: return@createActionLabel
                if(!tryAttachStdlibToModule(module, stdlib)) {
                    showWrongStdlibBalloon(stdlib)
                }
                notifications.updateAllNotifications()
            }

            createActionLabel("Do not show again") {
                disableNotification()
                notifications.updateAllNotifications()
            }
        }

    private fun tryAttachStdlibToModule(module: Module, stdlib: VirtualFile): Boolean {
        val roots = StandardLibraryRoots.fromFile(stdlib)
            ?: return false

        runWriteAction { roots.attachTo(module) }
        PropertiesComponent.getInstance().setValue(LIBRARY_LOCATION_KEY, stdlib.url)
        return true
    }

    private fun showWrongStdlibBalloon(stdlib: VirtualFile)  {
        project.showBalloon(
            "Invalid Rust standard library source path: `${stdlib.presentableUrl}`",
            NotificationType.ERROR
        )
    }

    private fun chooseDownloadLocation(parent: Component): VirtualFile? {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle(IdeBundle.message("dialog.directory.for.downloaded.files.title"))
            .withDescription(IdeBundle.message("dialog.directory.for.downloaded.files.description"))

        val defaultLocation = VfsUtil.getUserHomeDir()
        return FileChooser.chooseFile(descriptor, parent, project, defaultLocation)
    }

    private fun chooseStdlibLocation(parent: Component): VirtualFile? {
        val descriptor = FileChooserDescriptorFactory.createSingleLocalFileDescriptor()

        return FileChooser.chooseFile(descriptor, parent, project, null)
    }

    private fun disableNotification() {
        PropertiesComponent.getInstance(project).setValue(NOTIFICATION_STATUS_KEY, true)
    }

    private fun isNotificationDisabled(): Boolean {
        return PropertiesComponent.getInstance(project).getBoolean(NOTIFICATION_STATUS_KEY)
    }

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

        private val LOG = Logger.getInstance(MissingToolchainNotificationProvider::class.java)
    }
}
