/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.isDispatchThread
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.EditorNotificationPanel
import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.core.psi.RUST_STRUCTURE_CHANGE_TOPIC
import org.rust.lang.core.psi.RustStructureChangeListener
import org.rust.lang.core.psi.isNotRustFile
import org.rust.lang.core.psi.rustFile
import org.rust.openapiext.toPsiFile

class DetachedFileNotificationProvider(project: Project) : RsNotificationProvider(project) {

    override val VirtualFile.disablingKey: String get() = NOTIFICATION_STATUS_KEY + path

    init {
        project.messageBus.connect().apply {
            subscribe(RUST_STRUCTURE_CHANGE_TOPIC, object : RustStructureChangeListener {
                override fun rustStructureChanged(file: PsiFile?, changedElement: PsiElement?) {
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
        if (isUnitTestMode && !isDispatchThread) return null
        if (file.isNotRustFile || isNotificationDisabled(file)) return null

        val cargoProjects = project.cargoProjects
        // Handled by [MissingToolchainNotificationProvider]
        if (cargoProjects.findProjectForFile(file) == null) return null

        val psiFile = file.toPsiFile(project)?.rustFile ?: return null
        if (psiFile.crateRoot == null) {
            return createFileNotIncludedInModulesPanel(file)
        }
        return null
    }

    private fun createFileNotIncludedInModulesPanel(file: VirtualFile): RsEditorNotificationPanel =
        RsEditorNotificationPanel(DETACHED_FILE).apply {
            setText("File is not included in module tree, analysis is not available")
            createActionLabel("Do not show again") {
                disableNotification(file)
                updateAllNotifications()
            }
        }

    companion object {
        private const val NOTIFICATION_STATUS_KEY = "org.rust.hideDetachedFileNotifications"

        const val DETACHED_FILE = "DetachedFile"

        private val PROVIDER_KEY: Key<EditorNotificationPanel> = Key.create("Detached Rust file")
    }
}
