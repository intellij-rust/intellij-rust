package org.rust.ide.notifications

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RustToolchain
import org.rust.lang.core.psi.impl.isNotRustFile

/*
 * Warn user if no rust toolchain is found for the current module.
 */
class MissingToolchainNotificationProvider(
    private val project: Project,
    private val notifications: EditorNotifications
) : EditorNotifications.Provider<EditorNotificationPanel>() {

    init {
        project.messageBus.connect(project).subscribe(RustProjectSettingsService.TOOLCHAIN_TOPIC,
            object : RustProjectSettingsService.ToolchainListener {
                override fun toolchainChanged(newToolchain: RustToolchain?) {
                    notifications.updateAllNotifications()
                }
            })
    }

    override fun getKey(): Key<EditorNotificationPanel> = KEY

    override fun createNotificationPanel(file: VirtualFile, editor: FileEditor): EditorNotificationPanel? {
        if (file.isNotRustFile) return null
        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return null
        val toolchain = module.toolchain ?: return createBadToolchainPanel()
        if (!toolchain.looksLikeValidToolchain()) {
            return createBadToolchainPanel()
        }

        return null
    }

    private fun createBadToolchainPanel(): EditorNotificationPanel =
        EditorNotificationPanel().apply {
            setText("No Rust toolchain configured")
            createActionLabel("Setup toolchain", {
                project.service<RustProjectSettingsService>().configureToolchain()
            })
        }

    companion object {
        private val KEY: Key<EditorNotificationPanel> = Key.create("Setup Rust toolchain")
    }
}

