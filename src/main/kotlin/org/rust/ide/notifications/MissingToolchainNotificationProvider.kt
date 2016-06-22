package org.rust.ide.notifications

import com.intellij.ProjectTopics
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
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
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.UnstableVersion
import org.rust.cargo.toolchain.Version
import org.rust.cargo.util.StandardLibraryRoots
import org.rust.cargo.util.cargoProject
import org.rust.ide.utils.runWriteAction
import org.rust.ide.utils.service
import org.rust.lang.core.psi.impl.isNotRustFile
import org.rust.utils.download
import java.awt.Component

/**
 * Warn user if rust toolchain is not properly configured. Suggest to download stdlib.
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
        if (toolchain == null || !toolchain.looksLikeValidToolchain() || !toolchain.containsMetadataCommand()) {
            var title = "No Rust toolchain configured"
            if (toolchain == null || !toolchain.looksLikeValidToolchain())
                // NOP
            else if (!toolchain.containsMetadataCommand())
                title = "Configured Rust toolchain is incompatible with the plugin: required at least ${RustToolchain.CARGO_LEAST_COMPATIBLE_VERSION}, found ${toolchain.queryCargoVersion()}"

            return createBadToolchainPanel(title)
        }

        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
        return module.cargoProject?.let {
            if (!it.hasStandardLibrary)
                createLibraryAttachingPanel(module, toolchain)
            else
                null
        }
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
                        val version = toolchain.queryRustcVersion() ?: return
                        val url = sourcesArchiveUrlFromVersion(version)
                        library = download(url, "rust-${version.release}-src.zip", destination)
                    }

                    private fun sourcesArchiveUrlFromVersion(v: Version): String {
                        // We download sources from github and not from rust-lang.org, because we want zip archives. rust-lang.org
                        // hosts only .tar.gz.
                        val tag = if (v is UnstableVersion) v.commitHash else v.release
                        return "https://github.com/rust-lang/rust/archive/$tag.zip"
                    }

                    override fun onSuccess() {
                        if (module.isDisposed)
                            return

                        attachStdlibToModule(module, library!!)
                        notifications.updateAllNotifications()
                    }
                }

                task.queue()
            }

            createActionLabel("Attach") {
                attachStdlibToModule(module, chooseStdlibLocation(this) ?: return@createActionLabel)
                notifications.updateAllNotifications()
            }

            createActionLabel("Do not show again") {
                disableNotification()
                notifications.updateAllNotifications()
            }
        }

    private fun attachStdlibToModule(module: Module, stdlib: VirtualFile) {
        val roots = StandardLibraryRoots.fromFile(stdlib)
        if (roots == null) {
            PopupUtil.showBalloonForActiveFrame(
                "Invalid sources Rust standard library source path: `${stdlib.path}`",
                MessageType.ERROR
            )
            return
        }

        runWriteAction { roots.attachTo(module) }
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

    companion object {
        private val NOTIFICATION_STATUS_KEY = "org.rust.hideToolchainNotifications"

        private val PROVIDER_KEY: Key<EditorNotificationPanel> = Key.create("Setup Rust toolchain")
    }
}
