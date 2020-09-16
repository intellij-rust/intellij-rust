/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapiext.Testmark
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.ExternalFormatProcessor
import com.intellij.psi.formatter.FormatterUtil
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.Rustfmt
import org.rust.cargo.toolchain.Rustup.Companion.checkNeedInstallRustfmt
import org.rust.ide.formatter.RustfmtExternalFormatProcessor.Companion.formatWithRustfmtOrBuiltinFormatter
import org.rust.ide.formatter.processors.RsPostFormatProcessor
import org.rust.ide.formatter.processors.RsTrailingCommaFormatProcessor
import org.rust.ide.notifications.showBalloon
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.startOffset
import org.rust.openapiext.createSmartPointer
import org.rust.openapiext.document

/**
 * Replaces builtin intellij formatter with `Rustfmt` in the case of whole file formatting if
 * [org.rust.cargo.project.settings.RustProjectSettingsService.useRustfmt] option is enabled.
 * Used in a couple with [RsPostFormatProcessor].
 *
 * ## How the API works
 *
 * If [activeForFile] returns `true` for a file:
 * 1. [format] is invoked by the platform directly (instead of any other formatting machinery),
 * 2. OR the platform invokes all [PostFormatProcessor.processText], eventually invokes
 *  [RsPostFormatProcessor.processText] that delegates to [formatWithRustfmtOrBuiltinFormatter].
 *
 * [RsPostFormatProcessor] should be the only `postFormatProcessor` in the plugin.
 *
 * ## How we use the API
 *
 * `Rustfmt` is used only when formatting is requested by user explicitly. Otherwise, we
 * delegate formatting to the builtin formatter.
 * Also, `Rustfmt` doesn't support formatting a part of a file, so we use it only in the case
 * of whole file formatting. If part-file formatting is requested, we delegate it to the
 * builtin formatter
 */
@Suppress("UnstableApiUsage")
class RustfmtExternalFormatProcessor : ExternalFormatProcessor {

    override fun getId(): String = "rustfmt"

    override fun activeForFile(source: PsiFile): Boolean = isActiveForFile(source)

    // Never used by the platform?
    override fun indent(source: PsiFile, lineStartOffset: Int): String? = null

    override fun format(
        source: PsiFile,
        range: TextRange,
        canChangeWhiteSpacesOnly: Boolean,
        keepLineBreaks: Boolean // Always `false`?
    ): TextRange? {
        return formatWithRustfmtOrBuiltinFormatter(source, range, canChangeWhiteSpacesOnly)
    }

    companion object {
        fun isActiveForFile(source: PsiFile): Boolean =
            source is RsFile && source.project.rustSettings.useRustfmt

        private data class RustfmtContext(
            val rustfmt: Rustfmt,
            val cargoProject: CargoProject,
            val document: Document
        )

        private fun getRustfmtContext(source: PsiFile): RustfmtContext? {
            if (source !is RsFile) return null
            val file = source.virtualFile ?: return null
            val document = file.document ?: return null
            val project = source.project
            val cargoProject = project.cargoProjects.findProjectForFile(file) ?: return null
            val rustfmt = project.toolchain?.rustfmt() ?: return null
            return RustfmtContext(rustfmt, cargoProject, document)
        }

        fun formatWithRustfmtOrBuiltinFormatter(
            source: PsiFile,
            range: TextRange,
            canChangeWhiteSpacesOnly: Boolean,
        ): TextRange? {
            val tryRustfmt = !canChangeWhiteSpacesOnly
                && source.textRange == range
                && getFormattingReason() == FormattingReason.ReformatCode
            if (tryRustfmt) {
                val context = getRustfmtContext(source)
                if (context != null) {
                    return formatWithRustfmt(source, range, context)
                }
            }

            // Delegate unsupported cases to the built-in formatter
            return formatWithBuiltin(source, range, canChangeWhiteSpacesOnly)
        }

        private fun formatWithRustfmt(source: PsiFile, range: TextRange, context: RustfmtContext): TextRange? {
            Testmarks.rustfmtUsed.hit()

            val (rustfmt, cargoProject, document) = context
            val project = source.project

            var text: String? = null
           ApplicationManagerEx.getApplicationEx().runWriteActionWithCancellableProgressInDispatchThread("title", project, null) {
                if (checkNeedInstallRustfmt(project, cargoProject.workingDirectory)) {
                    return@runWriteActionWithCancellableProgressInDispatchThread
                }

                try {
                    text = rustfmt.reformatDocumentText(cargoProject, document)
                } catch (e: ExecutionException) {
                    e.message?.let { project.showBalloon(it, NotificationType.ERROR) }
                }
            }

            text?.let {
                document.setText(it)
            }

            return range
        }

        /**
         * Mimics to [com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl.reformatText] and
         * [com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl.formatRanges]
         */
        private fun formatWithBuiltin(source: PsiFile, range: TextRange, canChangeWhiteSpacesOnly: Boolean): TextRange? {
            val start = source.findElementAt(range.startOffset)?.createSmartPointer()
            val end = source.findElementAt(range.endOffset)?.createSmartPointer()
            val atEnd = range.endOffset == source.endOffset

            val formatter = CodeFormatterFacade(CodeStyle.getSettings(source), RsLanguage, canChangeWhiteSpacesOnly)
            formatter.processRange(source.node, range.startOffset, range.endOffset)

            if (!canChangeWhiteSpacesOnly) {
                Testmarks.builtinPostProcess.hit()
                val startOffset = if (range.startOffset == 0) 0 else start?.element?.startOffset
                val endOffset = if (atEnd) source.endOffset else end?.element?.endOffset
                if (startOffset != null && endOffset != null) {
                    RsTrailingCommaFormatProcessor.processText(
                        source,
                        TextRange(startOffset, endOffset),
                        CodeStyle.getSettings(source)
                    )
                }
            }
            return range
        }

        private enum class FormattingReason {
            ReformatCode,
            ReformatCodeBeforeCommit,
            Implicit
        }

        private fun getFormattingReason(): FormattingReason = when (CommandProcessor.getInstance().currentCommandName) {
            ReformatCodeProcessor.getCommandName() -> FormattingReason.ReformatCode
            FormatterUtil.getReformatBeforeCommitCommandName() -> FormattingReason.ReformatCodeBeforeCommit
            else -> FormattingReason.Implicit
        }
    }

    object Testmarks {
        val rustfmtUsed: Testmark = Testmark("rustfmtUsed")
        val builtinPostProcess: Testmark = Testmark("builtinPostProcess")
    }
}
