/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.google.gson.JsonParser
import com.intellij.lang.annotation.*
import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Result
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.PathUtil
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.CargoCode
import org.rust.cargo.toolchain.CargoMessage
import org.rust.cargo.toolchain.CargoSpan
import org.rust.cargo.toolchain.CargoTopMessage
import org.rust.cargo.util.cargoProjectRoot
import org.rust.ide.RsConstants
import org.rust.lang.core.psi.ext.module
import java.util.*

data class CargoCheckAnnotationInfo(
    val file: PsiFile
)

class CargoCheckAnnotationResult(commandOutput: List<String>, val project: Project)
    : ModificationTracker by PsiManager.getInstance(project).modificationTracker {

    companion object {
        private val parser = JsonParser()
        private val messageRegex = """\s*\{\s*"message".*""".toRegex()
    }

    val messages =
        commandOutput
            .filter { messageRegex.matches(it) }
            .map { parser.parse(it) }
            .filter { it.isJsonObject }
            .mapNotNull { CargoTopMessage.fromJson(it.asJsonObject) }
            .filterNot { it.message.message.startsWith("aborting due to") }
}

class RsCargoCheckAnnotator : ExternalAnnotator<CargoCheckAnnotationInfo, CargoCheckAnnotationResult>() {

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): CargoCheckAnnotationInfo? {
        if (!file.project.rustSettings.useCargoCheckAnnotator) return null

        return CargoCheckAnnotationInfo(file)
    }

    override fun doAnnotate(info: CargoCheckAnnotationInfo): CargoCheckAnnotationResult? =
        CachedValuesManager.getManager(info.file.project).createCachedValue {
            CachedValueProvider.Result.create(
                checkProject(info.file),
                PsiModificationTracker.MODIFICATION_COUNT)
        }.value

    override fun apply(file: PsiFile, annotationResult: CargoCheckAnnotationResult?, holder: AnnotationHolder) {
        annotationResult ?: return
        val doc = holder.currentAnnotationSession.file.viewProvider.document ?: throw AssertionError()

        for (topMessage in annotationResult.messages) {
            val message = topMessage.message
            val filePath = holder.currentAnnotationSession.file.virtualFile.path

            val severity =
                when (message.level) {
                    "error" -> HighlightSeverity.ERROR
                    "warning" -> HighlightSeverity.WEAK_WARNING
                    else -> HighlightSeverity.INFORMATION
                }

            val primarySpan = message.spans
                .filter { it.is_primary }
                .filter { it.isValid() }
                .firstOrNull()

            if (primarySpan != null) {
                val spanFilePath = PathUtil.toSystemIndependentName(primarySpan.file_name)
                // message for another file
                if (!filePath.endsWith(spanFilePath)) continue
            }

            // If spans are empty we add a "global" error
            if (primarySpan == null) {
                if (topMessage.target.src_path == filePath) {
                    // add a global annotation
                    holder.createAnnotation(severity, TextRange.EMPTY_RANGE, message.message).apply {
                        isFileLevelAnnotation = true
                        setNeedsUpdateOnTyping(true)
                        tooltip = "${escapeHtml(message.message)} ${message.code.formatAsLink().orEmpty()}"
                    }
                }
            } else {
                createAnnotation(primarySpan, message, severity, doc, holder).apply {
                    problemGroup = ProblemGroup { message.message }
                    setNeedsUpdateOnTyping(true)
                }
            }
        }
    }

    data class Group(val isList: Boolean, val lines: ArrayList<String>)

    fun formatLine(line: String): String {
        val (lastGroup, groups) =
            line.split("\n").fold(
                Pair(null as Group?, ArrayList<Group>()),
                { (group: Group?, acc: ArrayList<Group>), line ->
                    val (isListItem, line) = if (line.startsWith("-")) {
                        true to line.substring(2)
                    } else {
                        false to line
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

        return groups
            .map {
                if (it.isList) "<ul>${it.lines.joinToString("<li>", "<li>")}</ul>"
                else it.lines.joinToString("<br>")
            }.joinToString()
    }

    fun createAnnotation(span: CargoSpan, message: CargoMessage, severity: HighlightSeverity, doc: Document,
                         holder: AnnotationHolder): Annotation {

        fun toOffset(line: Int, column: Int) = doc.getLineStartOffset(line) + column

        // The compiler message lines and columns are 1 based while intellij idea are 0 based
        val textRange =
            TextRange(
                toOffset(span.line_start - 1, span.column_start - 1),
                toOffset(span.line_end - 1, span.column_end - 1))

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

            this
                .map { formatLine(it) }
                .joinToString("<br>")
        }

        return holder.createAnnotation(severity, textRange, message.message, tooltip)
    }

    fun CargoCode?.formatAsLink() =
        if (this?.code.isNullOrBlank()) null
        else "<a href=\"${RsConstants.ERROR_INDEX_URL}#${this?.code}\">${this?.code}</a>"

    fun CargoSpan.isValid() = line_end > line_start || (line_end == line_start && column_end >= column_start)

    // NB: executed asynchronously off EDT, so care must be taken not to access
    // disposed objects
    fun checkProject(file: PsiFile): CargoCheckAnnotationResult? {
        val (toolchain, projectPath, disposable: Disposable) = runReadAction {
            val module = file.module ?: return@runReadAction null
            if (module.isDisposed) return@runReadAction null
            val projectRoot = module.cargoProjectRoot ?: return@runReadAction null
            val toolchain = module.project.toolchain ?: return@runReadAction null
            Triple(toolchain, projectRoot.path, module)
        } ?: return null

        // We have to save the file to disk to give cargo a chance to check fresh file content.
        object : WriteAction<Unit>() {
            override fun run(result: Result<Unit>) {
                val fileDocumentManager = FileDocumentManager.getInstance()
                val document = fileDocumentManager.getDocument(file.virtualFile)
                if (document == null) {
                    fileDocumentManager.saveAllDocuments()
                } else if (fileDocumentManager.isDocumentUnsaved(document)) {
                    fileDocumentManager.saveDocument(document)
                }
            }
        }.execute()

        val output = toolchain.cargo(projectPath).checkProject(disposable)
        if (output.isCancelled) return null
        return CargoCheckAnnotationResult(output.stdoutLines, file.project)
    }
}
