/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.google.gson.JsonParser
import com.intellij.CommonBundle
import com.intellij.execution.ExecutionException
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.annotation.ProblemGroup
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.PathUtil
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.toolchain.*
import org.rust.lang.RsConstants
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.cargoWorkspace
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.saveAllDocuments
import java.nio.file.Path
import java.util.*

private val LOG = Logger.getInstance(RsCargoCheckAnnotator::class.java)

data class CargoCheckAnnotationInfo(
    val toolchain: RustToolchain,
    val projectPath: Path,
    val module: Module,
    val cargoPackage: CargoWorkspace.Package
)

class CargoCheckAnnotationResult(commandOutput: List<String>) {
    companion object {
        private val parser = JsonParser()
        private val messageRegex = """\s*\{.*"message".*""".toRegex()
    }

    val messages: List<CargoTopMessage> = commandOutput.asSequence()
        .filter { messageRegex.matches(it) }
        .map { parser.parse(it) }
        .filter { it.isJsonObject }
        .mapNotNull { CargoTopMessage.fromJson(it.asJsonObject) }
        .toList()
}

class RsCargoCheckAnnotator : ExternalAnnotator<CargoCheckAnnotationInfo, CargoCheckAnnotationResult>() {

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): CargoCheckAnnotationInfo? {
        if (file !is RsFile) return null
        if (!file.project.rustSettings.useCargoCheckAnnotator) return null
        val ws = file.cargoWorkspace ?: return null
        val module = ModuleUtil.findModuleForFile(file.virtualFile, file.project) ?: return null
        val toolchain = module.project.toolchain ?: return null
        val cargoPackage = file.containingCargoPackage
        if (cargoPackage?.origin != PackageOrigin.WORKSPACE) return null
        return CargoCheckAnnotationInfo(toolchain, ws.contentRoot, module, cargoPackage)
    }

    override fun doAnnotate(info: CargoCheckAnnotationInfo): CargoCheckAnnotationResult? = checkProject(info)

    override fun apply(file: PsiFile, annotationResult: CargoCheckAnnotationResult?, holder: AnnotationHolder) {
        if (annotationResult == null) return
        val doc = file.viewProvider.document
            ?: error("Can't find document for $file in Cargo check annotator")

        val filteredMessages = annotationResult.messages
            .mapNotNull { (topMessage) -> filterMessage(file, doc, topMessage) }
            // Cargo can duplicate some error messages when `--all-targets` attribute is used
            .distinct()
        for (message in filteredMessages) {
            // We can't control what messages cargo generates, so we can't test them well.
            // Let's use special message for tests to distinguish annotation from `RsCargoCheckAnnotator`
            val annotationMessage = if (isUnitTestMode) TEST_MESSAGE else message.message
            holder.createAnnotation(message.severity, message.textRange, annotationMessage, message.htmlTooltip)
                .apply {
                    problemGroup = ProblemGroup { annotationMessage }
                    setNeedsUpdateOnTyping(true)
                }
        }
    }

    companion object {
        const val TEST_MESSAGE = "CargoAnnotation"
    }
}

// NB: executed asynchronously off EDT, so care must be taken not to access disposed objects
private fun checkProject(info: CargoCheckAnnotationInfo): CargoCheckAnnotationResult? {
    val indicator = WriteAction.computeAndWait<ProgressIndicator, Throwable> {
        saveAllDocuments() // We have to save files to disk to give cargo a chance to check fresh file content
        BackgroundableProcessIndicator(
            info.module.project,
            "Analyzing File with Cargo Check",
            PerformInBackgroundOption.ALWAYS_BACKGROUND,
            CommonBundle.getCancelButtonText(),
            CommonBundle.getCancelButtonText(),
            true
        )
    }

    val output = try {
        ProgressManager.getInstance().runProcess(Computable {
            info.toolchain
                .cargoOrWrapper(info.projectPath)
                .checkProject(info.module.project, info.module, info.projectPath, info.cargoPackage)
        }, indicator)
    } catch (e: ExecutionException) {
        LOG.debug(e)
        return null
    }

    if (output.isCancelled) return null
    return CargoCheckAnnotationResult(output.stdoutLines)
}

private data class FilteredMessage(
    val severity: HighlightSeverity,
    val textRange: TextRange,
    val message: String,
    val htmlTooltip: String
)

private fun filterMessage(file: PsiFile, document: Document, message: RustcMessage): FilteredMessage? {
    if (message.message.startsWith("aborting due to") || message.message.startsWith("cannot continue")) {
        return null
    }

    val severity = when (message.level) {
        "error" -> HighlightSeverity.ERROR
        "warning" -> HighlightSeverity.WEAK_WARNING
        else -> HighlightSeverity.INFORMATION
    }

    val span = message.spans
        .firstOrNull { it.is_primary && it.isValid() }
        // Some error messages are global, and we *could* show then atop of the editor,
        // but they look rather ugly, so just skip them.
        ?: return null

    val syntaxErrors = listOf("expected pattern", "unexpected token")
    if (syntaxErrors.any { it in span.label.orEmpty() || it in message.message }) {
        return null
    }

    val spanFilePath = PathUtil.toSystemIndependentName(span.file_name)
    if (!file.virtualFile.path.endsWith(spanFilePath)) return null

    @Suppress("NAME_SHADOWING")
    fun toOffset(line: Int, column: Int): Int? {
        val line = line - 1
        val column = column - 1
        if (line >= document.lineCount) return null
        return (document.getLineStartOffset(line) + column)
            .takeIf { it <= document.textLength }
    }

    // The compiler message lines and columns are 1 based while intellij idea are 0 based
    val startOffset = toOffset(span.line_start, span.column_start)
    val endOffset = toOffset(span.line_end, span.column_end)
    val textRange = if (startOffset != null && endOffset != null && startOffset < endOffset) {
        TextRange(startOffset, endOffset)
    } else {
        return null
    }

    val tooltip = with(ArrayList<String>()) {
        val code = message.code.formatAsLink()
        add(escapeHtml(message.message) + if (code == null) "" else " $code")

        if (span.label != null && !message.message.startsWith(span.label)) {
            add(escapeHtml(span.label))
        }

        message.children
            .filter { !it.message.isBlank() }
            .map { "${it.level.capitalize()}: ${escapeHtml(it.message)}" }
            .forEach { add(it) }

        joinToString("<br>") { formatMessage(it) }
    }


    return FilteredMessage(severity, textRange, message.message, tooltip)
}

private fun RustcSpan.isValid() =
    line_end > line_start || (line_end == line_start && column_end >= column_start)

private fun ErrorCode?.formatAsLink() =
    if (this?.code.isNullOrBlank()) null
    else "<a href=\"${RsConstants.ERROR_INDEX_URL}#${this?.code}\">${this?.code}</a>"


private fun formatMessage(message: String): String {
    data class Group(val isList: Boolean, val lines: ArrayList<String>)

    val (lastGroup, groups) =
        message.split("\n").fold(
            Pair(null as Group?, ArrayList<Group>())
        ) { (group: Group?, acc: ArrayList<Group>), lineWithPrefix ->
            val (isListItem, line) = if (lineWithPrefix.startsWith("-")) {
                true to lineWithPrefix.substring(2)
            } else {
                false to lineWithPrefix
            }

            when {
                group == null -> Pair(Group(isListItem, arrayListOf(line)), acc)
                group.isList == isListItem -> {
                    group.lines.add(line)
                    Pair(group, acc)
                }
                else -> {
                    acc.add(group)
                    Pair(Group(isListItem, arrayListOf(line)), acc)
                }
            }
        }
    if (lastGroup != null && lastGroup.lines.isNotEmpty()) groups.add(lastGroup)

    return groups.joinToString {
        if (it.isList) "<ul>${it.lines.joinToString("<li>", "<li>")}</ul>"
        else it.lines.joinToString("<br>")
    }
}
