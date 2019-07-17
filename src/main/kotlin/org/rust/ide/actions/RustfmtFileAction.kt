/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.execution.ExecutionException
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.Rustfmt
import org.rust.cargo.toolchain.Rustup.Companion.checkNeedInstallRustfmt
import org.rust.lang.core.psi.isRustFile
import org.rust.openapiext.computeWithCancelableProgress
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.runWriteCommandAction
import org.rust.openapiext.virtualFile

class RustfmtFileAction : DumbAwareAction() {

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = getContext(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val (cargoProject, rustfmt, document) = getContext(e) ?: return
        check(!ApplicationManager.getApplication().isWriteAccessAllowed)
        val formattedText = cargoProject.project.computeWithCancelableProgress("Reformatting File with Rustfmt...") {
            reformatDocumentAndGetText(cargoProject, rustfmt, document)
        } ?: return
        cargoProject.project.runWriteCommandAction { document.setText(formattedText) }
    }

    private fun reformatDocumentAndGetText(cargoProject: CargoProject, rustfmt: Rustfmt, document: Document): String? {
        return try {
            if (checkNeedInstallRustfmt(cargoProject.project, cargoProject.workingDirectory)) return null
            rustfmt.reformatDocumentText(cargoProject, document)
        } catch (e: ExecutionException) {
            // Just easy way to know that something wrong happened
            if (isUnitTestMode) throw e
            null
        }
    }

    private fun getContext(e: AnActionEvent): Triple<CargoProject, Rustfmt, Document>? {
        val project = e.project ?: return null
        val rustfmt = project.toolchain?.rustfmt() ?: return null
        val editor = e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE) ?: getSelectedEditor(project) ?: return null
        val document = editor.document
        val file = document.virtualFile ?: return null
        if (!(file.isInLocalFileSystem && file.isRustFile)) return null
        val cargoProject = project.cargoProjects.findProjectForFile(file) ?: return null
        return Triple(cargoProject, rustfmt, document)
    }

    private fun getSelectedEditor(project: Project): Editor? =
        FileEditorManager.getInstance(project).selectedTextEditor
}
