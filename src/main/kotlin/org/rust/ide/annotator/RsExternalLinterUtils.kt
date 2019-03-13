/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.google.gson.JsonParser
import com.intellij.CommonBundle
import com.intellij.execution.ExecutionException
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.annotation.ProblemGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.PathUtil
import org.apache.commons.lang.StringEscapeUtils
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.toolchain.*
import org.rust.ide.annotator.RsExternalLinterFilteredMessage.Companion.filterMessage
import org.rust.ide.annotator.RsExternalLinterUtils.TEST_MESSAGE
import org.rust.ide.annotator.fixes.ApplySuggestionFix
import org.rust.lang.RsConstants
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.checkReadAccessNotAllowed
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.saveAllDocumentsAsTheyAre
import java.nio.file.Path
import java.util.*

object RsExternalLinterUtils {
    private val LOG: Logger = Logger.getInstance(RsExternalLinterUtils::class.java)
    const val TEST_MESSAGE: String = "RsExternalLint"

    /**
     * Returns (and caches if absent) lazily computed messages from external linter.
     *
     * Note: before applying this result you need to check that the PSI modification stamp of current project has not
     * changed after calling this method.
     *
     * @see PsiModificationTracker.MODIFICATION_COUNT
     */
    fun checkLazily(
        toolchain: RustToolchain,
        project: Project,
        owner: Disposable,
        workingDirectory: Path,
        packageName: String?
    ): Lazy<RsExternalLinterResult?>? {
        checkReadAccessAllowed()
        return CachedValuesManager.getManager(project)
            .getCachedValue(project) {
                // We want to run external linter in background thread and *without* read action.
                // And also we want to cache result of external linter because it is cargo package-global,
                // but annotator can be invoked separately for each file.
                // With `CachedValuesManager` our cached value should be invalidated on any PSI change.
                // Important note about this cache is that modification count will be stored AFTER computation
                // of a value. If we aren't in read action, PSI can be modified during computation of the value
                // and so an outdated value will be cached. So we can't use the cache without read action.
                // What we really want:
                // 1. Store current PSI modification count;
                // 2. Run external linter and retrieve results (in background thread and without read action);
                // 3. Try to cache result use modification count stored in (1). Result can be already outdated here.
                // We get such behavior by storing `Lazy` computation to the cache. Cache result is created in read
                // action, so it will be stored within correct PSI modification count. Then, we will retrieve the value
                // from `Lazy` in a background thread. The value will be computed or retrieved from the already computed
                // `Lazy` value.
                CachedValueProvider.Result.create(
                    lazy {
                        // This code will be executed out of read action in background thread
                        if (!isUnitTestMode) checkReadAccessNotAllowed()
                        checkWrapped(toolchain, project, owner, workingDirectory, packageName)
                    },
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            }
    }

    private fun checkWrapped(
        toolchain: RustToolchain,
        project: Project,
        owner: Disposable,
        workingDirectory: Path,
        packageName: String?
    ): RsExternalLinterResult? {
        val indicator = WriteAction.computeAndWait<ProgressIndicator, Throwable> {
            saveAllDocumentsAsTheyAre()
            BackgroundableProcessIndicator(
                project,
                "Analyzing Project with External Linter",
                PerformInBackgroundOption.ALWAYS_BACKGROUND,
                CommonBundle.getCancelButtonText(),
                CommonBundle.getCancelButtonText(),
                true
            )
        }
        return ProgressManager.getInstance().runProcess(
            Computable { check(toolchain, project, owner, workingDirectory, packageName) },
            indicator
        )
    }

    private fun check(
        toolchain: RustToolchain,
        project: Project,
        owner: Disposable,
        workingDirectory: Path,
        packageName: String?
    ): RsExternalLinterResult? {
        ProgressManager.checkCanceled()
        val output = try {
            toolchain
                .cargoOrWrapper(workingDirectory)
                .checkProject(project, owner, workingDirectory, packageName)
        } catch (e: ExecutionException) {
            LOG.error(e)
            return null
        }
        ProgressManager.checkCanceled()
        if (output.isCancelled) return null
        return RsExternalLinterResult(output.stdoutLines)
    }
}

fun AnnotationHolder.createAnnotationsForFile(file: RsFile, annotationResult: RsExternalLinterResult) {
    val cargoPackageOrigin = file.containingCargoPackage?.origin
    if (cargoPackageOrigin != PackageOrigin.WORKSPACE) return

    val doc = file.viewProvider.document
        ?: error("Can't find document for $file in external linter")

    val filteredMessages = annotationResult.messages
        .mapNotNull { (topMessage) -> filterMessage(file, doc, topMessage) }
        // Cargo can duplicate some error messages when `--all-targets` attribute is used
        .distinct()
    for (message in filteredMessages) {
        // We can't control what messages cargo generates, so we can't test them well.
        // Let's use special message for tests to distinguish annotation from external linter
        val annotationMessage = if (isUnitTestMode) TEST_MESSAGE else message.message
        createAnnotation(message.severity, message.textRange, annotationMessage, message.htmlTooltip)
            .apply {
                problemGroup = ProblemGroup { annotationMessage }
                setNeedsUpdateOnTyping(true)
                message.quickFixes.forEach(::registerFix)
            }
    }
}

class RsExternalLinterResult(commandOutput: List<String>) {
    val messages: List<CargoTopMessage> = commandOutput.asSequence()
        .filter { MESSAGE_REGEX.matches(it) }
        .map { PARSER.parse(it) }
        .filter { it.isJsonObject }
        .mapNotNull { CargoTopMessage.fromJson(it.asJsonObject) }
        .toList()

    companion object {
        private val PARSER = JsonParser()
        private val MESSAGE_REGEX = """\s*\{.*"message".*""".toRegex()
    }
}

private data class RsExternalLinterFilteredMessage(
    val severity: HighlightSeverity,
    val textRange: TextRange,
    val message: String,
    val htmlTooltip: String,
    val quickFixes: List<ApplySuggestionFix>
) {
    companion object {
        fun filterMessage(file: PsiFile, document: Document, message: RustcMessage): RsExternalLinterFilteredMessage? {
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

            val textRange = span.toTextRange(document) ?: return null

            val tooltip = with(ArrayList<String>()) {
                val code = message.code.formatAsLink()
                add(StringEscapeUtils.escapeHtml(message.message) + if (code == null) "" else " $code")

                if (span.label != null && !message.message.startsWith(span.label)) {
                    add(StringEscapeUtils.escapeHtml(span.label))
                }

                message.children
                    .filter { !it.message.isBlank() }
                    .map { "${it.level.capitalize()}: ${StringEscapeUtils.escapeHtml(it.message)}" }
                    .forEach { add(it) }

                joinToString("<br>") { formatMessage(it) }
            }

            return RsExternalLinterFilteredMessage(
                severity,
                textRange,
                message.message.capitalize(),
                tooltip,
                message.collectQuickFixes(file, document)
            )
        }
    }
}

private fun RustcSpan.isValid(): Boolean =
    line_end > line_start || (line_end == line_start && column_end >= column_start)

private fun ErrorCode?.formatAsLink(): String? =
    if (this?.code.isNullOrBlank()) null else "<a href=\"${RsConstants.ERROR_INDEX_URL}#${this?.code}\">${this?.code}</a>"

private fun RustcMessage.collectQuickFixes(file: PsiFile, document: Document): List<ApplySuggestionFix> {
    val quickFixes = mutableListOf<ApplySuggestionFix>()

    fun go(message: RustcMessage) {
        val span = message.spans.firstOrNull { it.is_primary && it.isValid() }
        createQuickFix(file, document, span, message.message)?.let { quickFixes.add(it) }
        message.children.forEach(::go)
    }

    go(this)
    return quickFixes
}

private fun createQuickFix(file: PsiFile, document: Document, span: RustcSpan?, message: String): ApplySuggestionFix? {
    if (span?.suggested_replacement == null || span.suggestion_applicability == null) return null
    val textRange = span.toTextRange(document) ?: return null
    val endElement = file.findElementAt(textRange.endOffset - 1) ?: return null
    val startElement = file.findElementAt(textRange.startOffset) ?: endElement
    return ApplySuggestionFix(message, span.suggested_replacement, startElement, endElement)
}

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
