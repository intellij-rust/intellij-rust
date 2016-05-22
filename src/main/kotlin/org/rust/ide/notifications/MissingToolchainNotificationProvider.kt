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
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.util.download.DownloadableFileService
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.cargo.util.attachStandardLibrary
import org.rust.cargo.util.findExternCrateRootByName
import org.rust.ide.utils.runWriteAction
import org.rust.ide.utils.service
import org.rust.lang.core.psi.impl.isNotRustFile
import java.awt.Component
import java.io.IOException

/*
 * Warn user if rust toolchain is not properly configured. Suggest to download stdlib.
 *
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

    override fun getKey(): Key<EditorNotificationPanel> = KEY

    override fun createNotificationPanel(file: VirtualFile, editor: FileEditor): EditorNotificationPanel? {
        if (file.isNotRustFile) return null
        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
        val toolchain = module.toolchain ?: return createBadToolchainPanel()
        if (!toolchain.looksLikeValidToolchain()) {
            return createBadToolchainPanel()
        }

        if (!isStdlibNotificationDisabled() && !module.hasStandardLibrary) {
            return createAttachLibraryPanel(module, toolchain)
        }

        return null
    }

    private fun createBadToolchainPanel(): EditorNotificationPanel =
        EditorNotificationPanel().apply {
            setText("No Rust toolchain configured")
            createActionLabel("Setup toolchain") {
                project.service<RustProjectSettingsService>().configureToolchain()
            }
        }

    private fun createAttachLibraryPanel(module: Module, toolchain: RustToolchain): EditorNotificationPanel =
        EditorNotificationPanel().apply {
            setText("No standard library sources found, some code insight will not work")
            createActionLabel("Download") {
                val destination = chooseDownloadLocation(this) ?: return@createActionLabel
                DownloadTask(module, toolchain, destination).queue()
            }

            createActionLabel("Attach") {
                val stdlib = chooseStdlibLocation(this) ?: return@createActionLabel
                runWriteAction {
                    module.attachStandardLibrary(stdlib)
                }
            }

            createActionLabel("Do not show again") {
                disableStdlibNotification()
                notifications.updateAllNotifications()
            }
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

    private fun disableStdlibNotification() {
        PropertiesComponent.getInstance(project).setValue(DO_NOT_SHOW_STDLIB_NOTIFICATION, true)
    }

    private fun isStdlibNotificationDisabled(): Boolean {
        return PropertiesComponent.getInstance(project).getBoolean(DO_NOT_SHOW_STDLIB_NOTIFICATION)
    }

    class DownloadTask(
        private val module: Module,
        private val toolchain: RustToolchain,
        private val destination: VirtualFile
    ) : Task.Backgroundable(module.project, "Stdlib download") {

        private var library: VirtualFile? = null

        override fun run(indicator: ProgressIndicator) {
            val version = toolchain.queryRustcVersion() ?: return
            val url = version.sourcesArchiveUrl
            library = download(url, "rust-${version.release}-src.zip", destination)
        }

        override fun onSuccess() {
            if (module.isDisposed) return
            val stdlib = library ?: return
            runWriteAction {
                module.attachStandardLibrary(stdlib)
            }
        }

        companion object {
            private fun download(url: String, fileName: String, destination: VirtualFile): VirtualFile? {
                val downloadService = DownloadableFileService.getInstance()
                val fileDescription = downloadService.createFileDescription(url, fileName)
                val downloader = downloadService.createDownloader(listOf(fileDescription), "rust")

                val downloadTo = VfsUtilCore.virtualToIoFile(destination)
                val file = try {
                    downloader.download(downloadTo).singleOrNull()?.first
                } catch (e: IOException) {
                    // TODO: probably should use IOExceptionDialog.showErrorDialog here,
                    // but let's ignore this for now and hope that a better way to get stdlib appears
                    null
                }

                return file?.let { LocalFileSystem.getInstance().refreshAndFindFileByIoFile(it) }
            }
        }
    }

    companion object {
        private val KEY: Key<EditorNotificationPanel> = Key.create("Setup Rust toolchain")
        private val DO_NOT_SHOW_STDLIB_NOTIFICATION = "do.not.show.stdlib.notification"

        private val Module.hasStandardLibrary: Boolean get() = findExternCrateRootByName(AutoInjectedCrates.std) != null
    }
}
