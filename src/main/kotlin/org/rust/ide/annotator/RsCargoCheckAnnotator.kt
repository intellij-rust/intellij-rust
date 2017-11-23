/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.google.gson.JsonParser
import com.intellij.execution.ExecutionException
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.annotation.ProblemGroup
import com.intellij.openapi.application.Result
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.PathUtil
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.toolchain.*
import org.rust.ide.RsConstants
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.cargoWorkspace
import org.rust.lang.core.psi.ext.containingCargoPackage
import java.nio.file.Path
import java.util.*

data class CargoCheckAnnotationInfo(
    val file: VirtualFile,
    val toolchain: RustToolchain,
    val projectPath: Path,
    val module: Module
)

class CargoCheckAnnotationResult(commandOutput: List<String>) {
    companion object {
        private val parser = JsonParser()
        private val messageRegex = """\s*\{\s*"message".*""".toRegex()
    }

    val messages: List<CargoTopMessage> =
        commandOutput
            .filter { messageRegex.matches(it) }
            .map { parser.parse(it) }
            .filter { it.isJsonObject }
            .mapNotNull { CargoTopMessage.fromJson(it.asJsonObject) }
}

class RsCargoCheckAnnotator : ExternalAnnotator<CargoCheckAnnotationInfo, CargoCheckAnnotationResult>() {

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): CargoCheckAnnotationInfo? {
        if (file !is RsFile) return null
        if (!file.project.rustSettings.useCargoCheckAnnotator) return null
        val ws = file.cargoWorkspace ?: return null
        val module = ModuleUtil.findModuleForFile(file.virtualFile, file.project) ?: return null
        val toolchain = module.project.toolchain ?: return null
        return CargoCheckAnnotationInfo(file.virtualFile, toolchain, ws.contentRoot, module)
    }

    override fun doAnnotate(info: CargoCheckAnnotationInfo): CargoCheckAnnotationResult? =
        CachedValuesManager.getManager(info.module.project)
            .getCachedValue(info.module, {
                CachedValueProvider.Result.create(
                    checkProject(info),
                    PsiModificationTracker.MODIFICATION_COUNT)
            })

    override fun apply(file: PsiFile, annotationResult: CargoCheckAnnotationResult?, holder: AnnotationHolder) {
        if (annotationResult == null) return

        val fileOrigin = (file as? RsElement)?.containingCargoPackage?.origin
        if (fileOrigin != PackageOrigin.WORKSPACE) return

        val doc = file.viewProvider.document
            ?: error("Can't find document for $file in Cargo check annotator")

        for ((topMessage) in annotationResult.messages) {
            val message = filterMessage(file, doc, topMessage) ?: continue
            holder.createAnnotation(message.severity, message.textRange, message.message, message.htmlTooltip)
                .apply {
                    problemGroup = ProblemGroup { message.message }
                    setNeedsUpdateOnTyping(true)
                }
        }
    }

}

// NB: executed asynchronously off EDT, so care must be taken not to access
// disposed objects
private fun checkProject(info: CargoCheckAnnotationInfo): CargoCheckAnnotationResult? {
    // We have to save the file to disk to give cargo a chance to check fresh file content.
    object : WriteAction<Unit>() {
        override fun run(result: Result<Unit>) {
            val fileDocumentManager = FileDocumentManager.getInstance()
            val document = fileDocumentManager.getDocument(info.file)
            if (document == null) {
                fileDocumentManager.saveAllDocuments()
            } else if (fileDocumentManager.isDocumentUnsaved(document)) {
                fileDocumentManager.saveDocument(document)
            }
        }
    }.execute()


    val output = try {
        info.toolchain.cargoOrWrapper(info.projectPath).checkProject(info.module, info.projectPath)
    } catch (e: ExecutionException) {
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
            Pair(null as Group?, ArrayList<Group>()),
            { (group: Group?, acc: ArrayList<Group>), lineWithPrefix ->
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
            })
    if (lastGroup != null && lastGroup.lines.isNotEmpty()) groups.add(lastGroup)

    return groups.joinToString {
        if (it.isList) "<ul>${it.lines.joinToString("<li>", "<li>")}</ul>"
        else it.lines.joinToString("<br>")
    }
}
