/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.CommonBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PairConsumer
import com.intellij.util.ui.UIUtil
import org.rust.RsBundle
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.tools.Rustfmt
import org.rust.cargo.toolchain.tools.Rustup
import org.rust.cargo.toolchain.tools.rustfmt
import org.rust.ide.settings.RsVcsConfiguration
import org.rust.lang.core.psi.isRustFile
import org.rust.openapiext.*
import org.rust.stdext.RsResult

class RustfmtCheckinFactory : CheckinHandlerFactory() {

    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext) = object : CheckinHandler() {
        override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent {
            return BooleanCommitOption.create(panel.project, this, false, RsBundle.message("run.rustfmt"),
                { isEnabled(panel) },
                { value: Boolean -> setEnabled(panel, value) })
        }

        override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>?): ReturnResult {
            if (!isEnabled(panel)) return ReturnResult.COMMIT

            FileDocumentManager.getInstance().saveAllDocuments()

            val project = panel.project
            var error: Pair<String, String?>? = null

            val applicableFiles = panel.virtualFiles.mapNotNull { fileContext(it, project) }
            for ((cargoProject, rustfmt, document) in applicableFiles) {
                val path = document.virtualFile?.path
                if (Rustup.checkNeedInstallRustfmt(cargoProject.project, cargoProject.workingDirectory)) {
                    error = RsBundle.message("rust.checkin.factory.fmt.rustfmt.not.installed.message") to path
                    break
                }
                val e = try {
                    execute(cargoProject, rustfmt, document, project).err()
                } catch (e: ProcessCanceledException) {
                    return ReturnResult.CANCEL
                }

                if (e != null) error = e to path

                FileDocumentManager.getInstance().saveDocument(document)
            }

            if (error != null) {
                return showErrorMessage(panel, executor, error)
            }

            return ReturnResult.COMMIT
        }
    }


    private fun execute(cargoProject: CargoProject, rustfmt: Rustfmt, document: Document, project: Project): RsResult<Unit, String> {
        val fileName = document.virtualFile?.presentableName

        val progressMessage = if (fileName != null) {
            RsBundle.message("action.Cargo.RustfmtFile.progress.file.text", fileName)
        } else {
            RsBundle.message("action.Cargo.RustfmtFile.progress.default.text")

        }
        val formattedText = cargoProject.project.computeWithCancelableProgress(progressMessage) {
            rustfmt.reformatTextDocument(cargoProject, document, project)
        } ?: return RsResult.Err(RsBundle.message("rust.checkin.factory.fmt.failed.message"))

        return formattedText.map {
            val commandName = if (fileName != null) {
                RsBundle.message("action.Cargo.RustfmtFile.file.text", fileName)
            } else {
                RsBundle.message("action.Cargo.RustfmtFile.default.text")
            }
            cargoProject.project.runWriteCommandAction(commandName) {
                document.setText(it.stdout)
            }
        }.mapErr {
            it.message ?: RsBundle.message("rust.checkin.factory.fmt.failed.message")
        }
    }

    private fun showErrorMessage(panel: CheckinProjectPanel, executor: CommitExecutor?, error: Pair<String, String?>): CheckinHandler.ReturnResult {
        val errorLines = error.first.split("\n", limit = 2)
        val filename = error.second
        val errorHeader = filename?.let {
            val header = RsBundle.message("rust.checkin.factory.fmt.header.message", it)
            "$header:<br/>"
        } ?: ""

        val (firstLineError, restOfTheError) = if (errorLines.size == 2) {
            errorLines
        } else if (errorLines.isEmpty()) {
            listOf(RsBundle.message("rust.checkin.factory.fmt.failed.message"), null)
        } else {
            listOf(errorLines.first(), null)
        }
        val errorDetails = restOfTheError?.let {
            "<br/><br/>${RsBundle.message("details")}<br/>$restOfTheError"
        } ?: ""

        val buttons = arrayOf(commitButtonMessage(executor, panel), CommonBundle.getCancelButtonText())
        val question: String = RsBundle.message("rust.checkin.factory.fmt.commit.anyway.question")
        val dialogText = RsBundle.message("dialog.message.html.body.br.b.b.body.html", errorHeader, firstLineError?:"", question, errorDetails)
        val answer = Messages.showDialog(panel.project, dialogText, RsBundle.message("notification.title.rustfmt"), null, buttons, 0, 1, UIUtil.getWarningIcon())
        return when (answer) {
            Messages.OK -> CheckinHandler.ReturnResult.COMMIT
            Messages.NO -> CheckinHandler.ReturnResult.CLOSE_WINDOW
            else -> CheckinHandler.ReturnResult.CANCEL
        }
    }

    private fun commitButtonMessage(executor: CommitExecutor?, panel: CheckinProjectPanel): @NlsContexts.Button String {
        return StringUtil.trimEnd(executor?.actionText ?: panel.commitActionName, "...")
    }

    private fun isEnabled(panel: CheckinProjectPanel) =
        RsVcsConfiguration.getInstance(panel.project).state.rustFmt

    private fun setEnabled(panel: CheckinProjectPanel, enabled: Boolean) {
        RsVcsConfiguration.getInstance(panel.project).state.rustFmt = enabled
    }

    private fun fileContext(file: VirtualFile, project: Project): FileContext? {
        val rustfmt = project.toolchain?.rustfmt() ?: return null
        val document = file.document ?: return null
        if (!file.isRustFile) return null
        val cargoProject = project.cargoProjects.findProjectForFile(file) ?: return null
        return FileContext(cargoProject, rustfmt, document)
    }

    private data class FileContext(
        val cargoProject: CargoProject,
        val rustfmt: Rustfmt,
        val document: Document
    )
}
