/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer
import com.intellij.openapi.project.ProjectLocator
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.Rustup
import org.rust.openapiext.virtualFile

/**
 * Checks rustfmt is installed when `Run rustfmt on save` is enabled.
 * Always permits to save the document and only shows the warning balloon (in [Rustup.checkNeedInstallRustfmt]).
 */
class RustfmtSavingVetoer : FileDocumentSynchronizationVetoer() {
    override fun maySaveDocument(document: Document, isSaveExplicit: Boolean): Boolean {
        val file = document.virtualFile ?: return true
        val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return true
        val cargoProject = project.cargoProjects.findProjectForFile(file) ?: return true

        if (project.rustSettings.runRustfmtOnSave) {
            Rustup.checkNeedInstallRustfmt(cargoProject.project, cargoProject.workingDirectory)
        }
        return true
    }
}
