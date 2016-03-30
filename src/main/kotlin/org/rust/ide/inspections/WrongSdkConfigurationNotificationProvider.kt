package org.rust.ide.inspections

import com.intellij.ProjectTopics
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.ModuleRootAdapter
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.rust.cargo.project.rustSdk
import org.rust.lang.core.psi.impl.isNotRustFile

class WrongSdkConfigurationNotificationProvider(
    private val project: Project,
    private val notifications: EditorNotifications
) : EditorNotifications.Provider<EditorNotificationPanel>() {

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
        if (module.rustSdk == null) {
            return createMissingSdkPanel()
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

    companion object {
        private val KEY: Key<EditorNotificationPanel> = Key.create("Setup Rust SDK")
    }
}
