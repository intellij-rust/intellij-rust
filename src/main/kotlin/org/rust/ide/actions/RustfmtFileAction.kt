/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.execution.ExecutionException
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAwareAction
import org.rust.RsBundle
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.tools.Rustfmt
import org.rust.cargo.toolchain.tools.Rustup.Companion.checkNeedInstallRustfmt
import org.rust.cargo.toolchain.tools.rustfmt
import org.rust.lang.core.psi.isRustFile
import org.rust.openapiext.*

class RustfmtFileAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = getContext(e) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val (cargoProject, rustfmt, document) = getContext(e) ?: return
        checkWriteAccessNotAllowed()
        val formattedText = cargoProject.project
            .computeWithCancelableProgress(RsBundle.message("action.Cargo.RustfmtFile.progress.default.text")) {
                reformatDocumentAndGetText(cargoProject, rustfmt, document)
            } ?: return
        val fileName = document.virtualFile?.presentableName
        val commandName = if (fileName != null) {
            RsBundle.message("action.Cargo.RustfmtFile.file.text", fileName)
        } else {
            RsBundle.message("action.Cargo.RustfmtFile.default.text")
        }
        cargoProject.project.runWriteCommandAction(commandName) {
            document.setText(formattedText)
        }
    }

    private fun reformatDocumentAndGetText(cargoProject: CargoProject, rustfmt: Rustfmt, document: Document): String? {
        return try {
            if (checkNeedInstallRustfmt(cargoProject.project, cargoProject.workingDirectory)) return null
            rustfmt.reformatDocumentTextOrNull(cargoProject, document)
        } catch (e: ExecutionException) {
            // Just easy way to know that something wrong happened
            if (isUnitTestMode) throw e
            null
        }
    }

    private fun getContext(e: AnActionEvent): Triple<CargoProject, Rustfmt, Document>? {
        val project = e.project ?: return null
        val rustfmt = project.toolchain?.rustfmt() ?: return null
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val document = editor.document
        val file = document.virtualFile ?: return null
        if (!(file.isInLocalFileSystem && file.isRustFile)) return null
        val cargoProject = project.cargoProjects.findProjectForFile(file) ?: return null
        return Triple(cargoProject, rustfmt, document)
    }
}
