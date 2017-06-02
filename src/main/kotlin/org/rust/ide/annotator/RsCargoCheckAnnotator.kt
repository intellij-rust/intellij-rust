package org.rust.ide.annotator

import com.google.gson.JsonParser
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.annotation.ProblemGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiFile
import com.intellij.util.PathUtil
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.CargoMessage
import org.rust.cargo.toolchain.CargoTopMessage
import java.util.concurrent.ConcurrentHashMap

data class TEAAnnotationInfo(val file: PsiFile, val editor: Editor)

class TEAAnnotationResult(commandOutput: List<String>) {
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
                    assert(false); throw AssertionError()
                }
            }
        }.map {
            parser.parse(it)
        }.mapNotNull {
            if (it.isJsonObject) it.asJsonObject else null
        }.mapNotNull {
            CargoTopMessage.fromJson(it)
        }
    }
}

class RsCargoCheckAnnotator : ExternalAnnotator<TEAAnnotationInfo, TEAAnnotationResult>() {
    private val cache = ConcurrentHashMap<Project, TEAAnnotationResult>()

    init {
        ApplicationManager.getApplication().messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    for (project in cache.keys) {
                        val relevantEvents = events.filterNotNull().filter{
                            ProjectFileIndex.getInstance(project).isInSourceContent(it.file!!)
                        }
                        if (relevantEvents.isNotEmpty()) {
                            //Remove the result to force recalculation
                            cache.remove(project)
                        }

                    }
                }

                override fun before(events: MutableList<out VFileEvent>) {}
            })
    }

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): TEAAnnotationInfo? {

        val ret : TEAAnnotationInfo?

        if (hasErrors) {
            ret = TEAAnnotationInfo(file, editor)
        } else {
            ret = TEAAnnotationInfo(file, editor)
        }

        return ret
    }

    override fun doAnnotate(info: TEAAnnotationInfo): TEAAnnotationResult? {
        return cache.getOrPut(info.file.project, { checkProject(info.file) ?: TEAAnnotationResult(emptyList()) })
    }

    override fun apply(file: PsiFile, annotationResult: TEAAnnotationResult?, holder: AnnotationHolder) {

        if (annotationResult == null)
            return

        annotationResult.messages.forEach {
            val annotFile = holder.currentAnnotationSession.file.virtualFile.name
            val annotFilePath = holder.currentAnnotationSession.file.virtualFile.path
            val severity = when (it.message.level) {
                "error" -> HighlightSeverity.ERROR
                "warning" -> HighlightSeverity.WARNING
                else -> { throw AssertionError() }
            }
            val shortErrorStr = it.message.message
            val codeStr = it.message.code
            val target = it.target

            //If spans are empty we add a "global" error
            if (it.message.spans.isEmpty()) {
                if (it.target.src_path != annotFilePath) {
                    //not main file, skip global annotation
                } else {
                    //add a global annotation
                    val annot = holder.createAnnotation(severity, TextRange.EMPTY_RANGE, shortErrorStr)

                    annot.isFileLevelAnnotation = true
                    annot.setNeedsUpdateOnTyping(true)
                    annot.tooltip = escapeHtml(shortErrorStr) + if(codeStr != "") "<hr/>$codeStr" else ""
                }
                return
            }

            val problemGroup = ProblemGroup { shortErrorStr }

            it.message.spans.filter {
                annotFilePath == it.file_name
            }.forEach {
                val doc = holder.currentAnnotationSession.file.viewProvider.document ?: throw AssertionError()

                fun toOfs(line: Int, col: Int): Int {
                    val lineStart = doc.getLineStartOffset(line)
                    val lineEnd = doc.getLineEndOffset(line)
                    @Suppress("UNUSED_VARIABLE")
                    val colsInLine = lineEnd - lineStart

                    //col == colsInLine means end of line?
                    //It is possible to get it in the compiler message

                    //We pass on this check since the compiler seems to violate it liberally
                    //if (col > colsInLine)
                    //throw AssertionError()

                    return lineStart + col
                }

                //FIXME: Sometimes rustc outputs an end line smaller than the start line.
                //       Assuming this is a bug in rustc and not a feature, this condition should be
                //       reverted to an assert in the future.
                if (it.line_end < it.line_start || it.line_end == it.line_start && it.column_end < it.column_start)
                    return@forEach

                //The compiler message lines and columns are 1 based while intellij idea are 0 based
                val textRange = TextRange(toOfs(it.line_start - 1, it.column_start - 1), toOfs(it.line_end - 1, it.column_end - 1))

                val short = if (it.is_primary) {
                    //Special case - if this is a primary trait then we have to add
                    //the message text (since it may have a label but the label is only extra info).
                    shortErrorStr
                } else {
                    //Short message is the description. If there's a label we use it,
                    //and if not we use the original error message.
                    //In any case we attach the error code if there.
                    it.label ?: shortErrorStr
                }

                //In the tooltip we give additional info - the children messages
                val extra = (if (codeStr != "") "$codeStr<br/>" else "") +
                    (if (it.is_primary && it.label != null) "${escapeHtml(it.label)}<br/>" else "") // + childrenMsg
                val tooltip = short + if (extra.isBlank()) "" else "<hr/>" + extra

                val spanSeverity = if (it.is_primary) severity else HighlightSeverity.INFORMATION // HighlightSeverity.WEAK_WARNING
                val annot = holder.createAnnotation(spanSeverity, textRange, short)

                //See @holder.createAnnotation with tooltip for why we wrap the message like this
                annot!!.tooltip = "<html>${escapeHtml(tooltip)}</html>"
                annot.problemGroup = problemGroup
                annot.setNeedsUpdateOnTyping(true)
            }
        }
    }

    fun checkProject(file: PsiFile): TEAAnnotationResult? {
        val module = ModuleUtilCore.findModuleForPsiElement(file) ?: return null
        val moduleDirectory = PathUtil.getParentPath(module.moduleFilePath)
        val output = module.project.toolchain?.cargo(moduleDirectory)?.checkFile(module)
        output ?: return null

        if (output.isCancelled) {
            return null
        }

        val parser = JsonParser()
        return TEAAnnotationResult(output.stdoutLines)
    }
}
