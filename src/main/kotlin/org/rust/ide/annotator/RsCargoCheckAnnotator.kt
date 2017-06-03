package org.rust.ide.annotator

import com.google.gson.JsonParser
import com.intellij.lang.annotation.*
import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.application.Result
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.PathUtil
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.CargoMessage
import org.rust.cargo.toolchain.CargoSpan
import org.rust.cargo.toolchain.CargoTopMessage
import java.io.File

data class CargoCheckAnnotationInfo(val file: PsiFile, val editor: Editor)

class CargoCheckAnnotationResult(commandOutput: List<String>, val project: Project): ModificationTracker {

    private val modificationTracker = PsiManager.getInstance(project).modificationTracker
    private val parser = JsonParser()
    //private val LOG = Logger.getInstance(RsCargoCheckAnnotator::class.java)

    val messages: List<CargoTopMessage> =
        commandOutput.filter {
            it.trimStart().startsWith("{")
        }.filter {
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

    override fun getModificationCount(): Long {
        val modificationCount = modificationTracker.modificationCount
        //LOG.info("getModificationCount = $modificationCount")
        return modificationCount
    }
}

class RsCargoCheckAnnotator : ExternalAnnotator<CargoCheckAnnotationInfo, CargoCheckAnnotationResult>() {

    private fun getCachedResult(file: PsiFile) =
        CachedValuesManager.getManager(file.project).createCachedValue {
            CachedValueProvider.Result.create(
                checkProject(file),
                PsiModificationTracker.MODIFICATION_COUNT)

        }

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): CargoCheckAnnotationInfo? {
        return CargoCheckAnnotationInfo(file, editor)
    }

    override fun doAnnotate(info: CargoCheckAnnotationInfo) = getCachedResult(info.file).value

    override fun apply(file: PsiFile, annotationResult: CargoCheckAnnotationResult?, holder: AnnotationHolder) {
        annotationResult ?: return
        val doc = holder.currentAnnotationSession.file.viewProvider.document ?: throw AssertionError()

        for (topMessage in annotationResult.messages) {
            val message = topMessage.message
            val filePath = holder.currentAnnotationSession.file.virtualFile.path

            val severity = when (message.level) {
                "error" -> HighlightSeverity.ERROR
                "warning" -> HighlightSeverity.WEAK_WARNING
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

        val extraMessage = run {
            val codeHtml = if (message.code?.code.isNullOrBlank()) "" else "${message.code?.code}<br/>"
            val labelHtml = if (span.is_primary && span.label != null) "${escapeHtml(span.label)}<br/>" else ""
            codeHtml + labelHtml
        }

        val tooltip = shortMessage + if (extraMessage.isBlank()) "" else "<hr/>" + extraMessage
        val spanSeverity = if (span.is_primary) severity else HighlightSeverity.INFORMATION
        val annotation = holder.createAnnotation(spanSeverity, textRange, shortMessage)
        annotation!!.tooltip = "<html>${escapeHtml(tooltip)}</html>"
        return annotation
    }

    fun isValidSpan(span: CargoSpan) =
        span.line_end > span.line_start
            || (span.line_end == span.line_start && span.column_end >= span.column_start)

    fun checkProject(file: PsiFile): CargoCheckAnnotationResult? {
        val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return null

        // We have to save the file to disk to give cargo a chance to check fresh file content.
        object : WriteAction<Unit>() {
            override fun run(result: Result<Unit>) {
                val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)
                if (document != null) {
                    FileDocumentManager.getInstance().saveDocument(document)
                } else {
                    FileDocumentManager.getInstance().saveAllDocuments()
                }
            }
        }.execute()

        val moduleDirectory = PathUtil.getParentPath(module.moduleFilePath)
        val output = module.project.toolchain?.cargo(moduleDirectory)?.checkFile(module)
        output ?: return null
        if (output.isCancelled) return null
        return CargoCheckAnnotationResult(output.stdoutLines, file.project)
    }
}
