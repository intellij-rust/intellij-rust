/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.notifications

import com.intellij.ide.impl.UntrustedProjectEditorNotificationPanel
import com.intellij.ide.impl.isTrusted
import com.intellij.ide.impl.setTrusted
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.isCargoToml
import org.rust.ide.security.isNewTrustedProjectApiAvailable
import org.rust.lang.core.psi.isRustFile

class RsUntrustedNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>(), DumbAware {

    override fun getKey(): Key<EditorNotificationPanel> = PROVIDER_KEY

    @Suppress("UnstableApiUsage")
    override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
        if (project.isTrusted()) return null
        if (isNewTrustedProjectApiAvailable) return null
        if (!(file.isRustFile || file.isCargoToml)) return null

        val cargoProjects = project.cargoProjects
        if (!cargoProjects.initialized) return null

        return UntrustedProjectEditorNotificationPanel(project, fileEditor) {
            project.setTrusted(true)
        }
    }

    companion object {
        private val PROVIDER_KEY: Key<EditorNotificationPanel> = Key.create("Untrusted Rust project")
    }
}
