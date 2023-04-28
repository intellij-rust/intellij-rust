/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.psi.PsiFile
import com.intellij.psi.formatter.FormatterUtil
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustfmtSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.tools.Rustup.Companion.checkNeedInstallRustfmt
import org.rust.cargo.toolchain.tools.rustfmt
import org.rust.lang.core.psi.RsFile
import org.rust.openapiext.document
import org.rust.openapiext.execute
import org.rust.openapiext.ignoreExitCode
import org.rust.openapiext.runProcess
import org.rust.stdext.enumSetOf
import org.rust.stdext.unwrapOrThrow

class RustfmtFormattingService : AsyncDocumentFormattingService() {

    override fun getFeatures(): Set<FormattingService.Feature> = FEATURES

    override fun canFormat(file: PsiFile): Boolean =
        file is RsFile && file.project.rustfmtSettings.useRustfmt && getFormattingReason() == FormattingReason.ReformatCode

    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
        val context = request.context
        val project = context.project
        val file = context.virtualFile ?: return null
        val document = file.document ?: return null
        val cargoProject = project.cargoProjects.findProjectForFile(file) ?: return null
        val rustfmt = project.toolchain?.rustfmt() ?: return null

        return object : FormattingTask {
            private val indicator: ProgressIndicatorBase = ProgressIndicatorBase()

            override fun run() {
                RustfmtTestmarks.RustfmtUsed.hit()

                if (checkNeedInstallRustfmt(project, cargoProject.workingDirectory)) {
                    request.onTextReady(request.documentText)
                    return
                }

                rustfmt.createCommandLine(cargoProject, document)?.execute(
                    cargoProject.project,
                    stdIn = request.documentText.toByteArray(),
                    runner = {
                        addProcessListener(object : CapturingProcessAdapter() {
                            override fun processTerminated(event: ProcessEvent) {
                                val exitCode = event.exitCode
                                if (exitCode == 0) {
                                    request.onTextReady(output.stdout)
                                } else {
                                    request.onError("Rustfmt", output.stderr)
                                }
                            }
                        })
                        runProcess(indicator)
                    }
                )?.ignoreExitCode()?.unwrapOrThrow()
            }

            override fun cancel(): Boolean {
                indicator.cancel()
                return true
            }

            override fun isRunUnderProgress(): Boolean = true
        }
    }

    override fun getNotificationGroupId(): String = "Rust Plugin"

    override fun getName(): String = "rustfmt"

    companion object {
        private val FEATURES: Set<FormattingService.Feature> = enumSetOf()

        private enum class FormattingReason {
            ReformatCode,
            ReformatCodeBeforeCommit,
            Implicit
        }

        private fun getFormattingReason(): FormattingReason =
            when (CommandProcessor.getInstance().currentCommandName) {
                ReformatCodeProcessor.getCommandName() -> FormattingReason.ReformatCode
                FormatterUtil.getReformatBeforeCommitCommandName() -> FormattingReason.ReformatCodeBeforeCommit
                else -> FormattingReason.Implicit
            }
    }
}
