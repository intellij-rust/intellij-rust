package org.rust.ide.inspections

import com.intellij.ProjectTopics
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootAdapter
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.util.download.DownloadableFileService
import org.rust.cargo.project.RustcVersion
import org.rust.cargo.project.module.util.locateRustSources
import org.rust.cargo.project.rustSdk
import org.rust.lang.core.psi.impl.isNotRustFile

/*
 * Warn user if no rust sdk is found for the current module or if the sdk lacks standard library sources.
 */
class WrongSdkConfigurationNotificationProvider(
    private val project: Project,
    private val notifications: EditorNotifications
) : EditorNotifications.Provider<EditorNotificationPanel>()

// Indexing begins as soon as rust sources are downloaded. If this is not `DumbAware` then
// notification panel will stay until the indexing ends. Let's make this `DumbAware` to make the panel
// go away immediately and avoid user confusion.
  , DumbAware
{

    init {
        project.messageBus.connect(project).subscribe(ProjectTopics.PROJECT_ROOTS,
            object : ModuleRootAdapter() {
                override fun rootsChanged(event: ModuleRootEvent?) {
                    notifications.updateAllNotifications()
                }
            })
    }

    override fun getKey(): Key<EditorNotificationPanel> = KEY

    override fun createNotificationPanel(file: VirtualFile, editor: FileEditor): EditorNotificationPanel? {
        if (file.isNotRustFile) return null
        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
        val sdk = module.rustSdk ?: return createMissingSdkPanel()
        val homePath = sdk.homePath ?: return createMissingSdkPanel()

        if (module.locateRustSources() == null) {
            return createAttachSourcesPanel(sdk, homePath)
        }

        return null
    }

    private fun createMissingSdkPanel(): EditorNotificationPanel =
        EditorNotificationPanel().apply {
            setText(ProjectBundle.message("project.sdk.not.defined"))
            createActionLabel(ProjectBundle.message("project.sdk.setup"), {
                ProjectSettingsService.getInstance(project).chooseAndSetSdk()
            })
        }

    private fun createAttachSourcesPanel(sdk: Sdk, sdkHomePath: String): EditorNotificationPanel {
        val panel = EditorNotificationPanel()
        panel.setText("Standard library sources are not found. Some features will not work.")

        panel.createActionLabel("Download and attach sources", {
            // XXX: this queries external process and then fetches a file from a network.
            // Everything can go wrong here.
            val version = RustcVersion.queryFromRustc(sdkHomePath) ?: return@createActionLabel
            val src = downloadSources(panel, version) ?: return@createActionLabel

            sdk.sdkModificator.apply {
                removeRoots(OrderRootType.CLASSES)
                addRoot(src, OrderRootType.CLASSES)
                commitChanges()
            }
        })

        return panel
    }

    private fun downloadSources(panel: EditorNotificationPanel, version: RustcVersion): VirtualFile? {
        val url = version.sourcesArchiveUrl
        val downloadService = DownloadableFileService.getInstance()
        val fileDescription = downloadService.createFileDescription(url, "rust-${version.release}-src.zip")
        val downloader = downloadService.createDownloader(listOf(fileDescription), "rust")

        // Ask user where to download the archive with source code.
        val downloadTo = null
        val (file, @Suppress("UNUSED_VARIABLE") description) =
            downloader.downloadWithProgress(downloadTo, project, panel)?.singleOrNull() ?: return null

        return file
    }

    companion object {
        private val KEY: Key<EditorNotificationPanel> = Key.create("Setup Rust SDK")
    }
}
