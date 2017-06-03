package org.rust.ide.annotator

import com.google.gson.JsonParser
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.annotation.ProblemGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.PathUtil
import com.intellij.util.containers.HashMap
import com.intellij.util.containers.ImmutableList
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.CargoMessage
import org.rust.cargo.toolchain.CargoSpan
import org.rust.cargo.toolchain.CargoTopMessage
import org.rust.cargo.util.modules
import java.util.concurrent.ConcurrentHashMap

data class CargoCheckAnnotationInfo(val file: PsiFile, val editor: Editor)

class CargoCheckAnnotationResult(commandOutput: List<String>) {
    var messages = emptyList<CargoTopMessage>()

    init {
        val parser = JsonParser()
        messages += commandOutput.filter {
            // Apparently some lines also have human readable messages. We ignore any lines not beginning with '{'
            val trimmed = it.trimStart()
            trimmed.isNotEmpty() && trimmed[0] == '{'
        }.filter {
            // We check the first field name by finding the first " then reading until another " (we expect
            // that a '{' present and don't check for it).
            val first = it.indexOfFirst { it == '"' }
            assert(first != -1)
            val second = it.indexOf('"', first + 1)
            assert(second != -1)

            when (it.subSequence(first + 1, second)) {
                "message" -> true
                "features" -> false
                else -> {
                    assert(false)
                    throw AssertionError()
                }
            }
        }.map {
            parser.parse(it)
        }.mapNotNull {
            if (it.isJsonObject)
                CargoTopMessage.fromJson(it.asJsonObject)
            else
                null
        }
    }

    companion object {
        val empty = CargoCheckAnnotationResult(emptyList())
    }
}

class RsCargoCheckAnnotator : ExternalAnnotator<CargoCheckAnnotationInfo, CargoCheckAnnotationResult>() {

    private fun getCachedResult(file: PsiFile) =
        CachedValuesManager.getManager(file.project).createCachedValue {
            CachedValueProvider.Result.create(
                checkProject(file),
                PsiModificationTracker.MODIFICATION_COUNT
            )
        }

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): CargoCheckAnnotationInfo? =
        CargoCheckAnnotationInfo(file, editor)

    override fun doAnnotate(info: CargoCheckAnnotationInfo) = getCachedResult(info.file).value

    override fun apply(file: PsiFile, annotationResult: CargoCheckAnnotationResult?, holder: AnnotationHolder) {
        annotationResult ?: return
        val doc = holder.currentAnnotationSession.file.viewProvider.document ?: throw AssertionError()

        for (topMessage in annotationResult.messages) {
            val message = topMessage.message
            val filePath = holder.currentAnnotationSession.file.virtualFile.path

            val severity = when (message.level) {
                "error" -> HighlightSeverity.ERROR
                "warning" -> HighlightSeverity.WARNING
                else -> throw AssertionError()
            }

            val codeStr = message.code?.code

            // If spans are empty we add a "global" error
            if (message.spans.isEmpty()) {
                if (topMessage.target.src_path == filePath) {
                    // add a global annotation
                    val annotation = holder.createAnnotation(severity, TextRange.EMPTY_RANGE, message.message)
                    annotation.isFileLevelAnnotation = true
                    annotation.setNeedsUpdateOnTyping(true)
                    annotation.tooltip = escapeHtml(message.message) + if (codeStr != "") "<hr/>$codeStr" else ""
                }
            } else {
                val problemGroup = ProblemGroup { message.message }

                val relevantSpans = message.spans.filter {
                    val spanFilePath = PathUtil.getCanonicalPath(it.file_name)
                    filePath.endsWith(spanFilePath) && isValidSpan(it)
                }

                for (span in relevantSpans) {
                    val annotation = createAnnotation(span, message, severity, doc, holder)
                    annotation.problemGroup = problemGroup
                    annotation.setNeedsUpdateOnTyping(true)
                }
            }
        }
    }

    fun createAnnotation(span: CargoSpan, message: CargoMessage, severity: HighlightSeverity, doc: Document,
                         holder: AnnotationHolder): Annotation {

        fun toOffset(line: Int, column: Int): Int {
            val lineStart = doc.getLineStartOffset(line)
            return lineStart + column
        }

        // The compiler message lines and columns are 1 based while intellij idea are 0 based
        val textRange =
            TextRange(toOffset(span.line_start - 1, span.column_start - 1),
                toOffset(span.line_end - 1, span.column_end - 1))

        val shortMessage =
            when (span.is_primary) {
                true -> message.message
                else -> span.label ?: message.message
            }

        val extraMessage =
            (if (message.code?.code != "") "${message.code?.code}<br/>" else "") +
                (if (span.is_primary && span.label != null) "${escapeHtml(span.label)}<br/>" else "")

        val tooltip = shortMessage + if (extraMessage.isBlank()) "" else "<hr/>" + extraMessage
        val spanSeverity = if (span.is_primary) severity else HighlightSeverity.INFORMATION
        val annotation = holder.createAnnotation(spanSeverity, textRange, shortMessage)
        annotation!!.tooltip = "<html>${escapeHtml(tooltip)}</html>"
        return annotation
    }

    fun isValidSpan(span: CargoSpan) =
        // FIXME: Sometimes rustc outputs an end line smaller than the start line.
        //       Assuming this is a bug in rustc and not a feature, this condition should be
        //       reverted to an assert in the future.
        span.line_end > span.line_start
            || (span.line_end == span.line_start && span.column_end >= span.column_start)

    fun checkProject(file: PsiFile): CargoCheckAnnotationResult {
        val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return CargoCheckAnnotationResult.empty
        val moduleDirectory = PathUtil.getParentPath(module.moduleFilePath)
        val output = module.project.toolchain?.cargo(moduleDirectory)?.checkFile(module)
        output ?: return CargoCheckAnnotationResult.empty
        if (output.isCancelled) return CargoCheckAnnotationResult.empty
        return CargoCheckAnnotationResult(output.stdoutLines)
    }
}
