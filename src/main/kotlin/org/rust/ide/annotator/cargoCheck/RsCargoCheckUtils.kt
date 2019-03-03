/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.cargoCheck

import com.google.common.annotations.VisibleForTesting
import com.google.gson.JsonParser
import com.intellij.CommonBundle
import com.intellij.execution.ExecutionException
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.annotation.ProblemGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.TrailingSpacesStripper
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
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
import org.rust.ide.annotator.cargoCheck.RsCargoCheckFilteredMessage.Companion.filterMessage
import org.rust.ide.annotator.cargoCheck.RsCargoCheckUtils.TEST_MESSAGE
import org.rust.lang.RsConstants
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.checkReadAccessNotAllowed
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.saveAllDocuments
import java.lang.reflect.Field
import java.nio.file.Path
import java.util.*

object RsCargoCheckUtils {
    private val LOG: Logger = Logger.getInstance(RsCargoCheckUtils::class.java)
    const val TEST_MESSAGE: String = "CargoCheckAnnotation"

    /**
     * Returns (and caches if absent) lazily computed `cargo check` result.
     *
     * Note: before applying this result you need to check that the PSI modification stamp of current project has not
     * changed after calling this method.
     *
     * @see PsiModificationTracker.MODIFICATION_COUNT
     */
    fun checkLazily(
        toolchain: RustToolchain,
        project: Project,
        owner: ComponentManager,
        workingDirectory: Path,
        packageName: String?,
        isOnFly: Boolean
    ): Lazy<RsCargoCheckAnnotationResult?>? {
        checkReadAccessAllowed()
        return CachedValuesManager.getManager(project)
            .getCachedValue(owner) {
                // We want to run `cargo check` in background thread and *without* read action.
                // And also we want to cache result of `cargo check` because `cargo check` is cargo package-global,
                // but annotator can be invoked separately for each file.
                // With `CachedValuesManager` our cached value should be invalidated on any PSI change.
                // Important note about this cache is that modification count will be stored AFTER computation
                // of a value. If we aren't in read action, PSI can be modified during computation of the value
                // and so an outdated value will be cached. So we can't use the cache without read action.
                // What we really want:
                // 1. Store current PSI modification count;
                // 2. Run `cargo check` and retrieve results (in background thread and without read action);
                // 3. Try to cache result use modification count stored in (1). Result can be already outdated here.
                // We get such behavior by storing `Lazy` computation to the cache. Cache result is created in read
                // action, so it will be stored within correct PSI modification count. Then, we will retrieve the value
                // from `Lazy` in a background thread. The value will be computed or retrieved from the already computed
                // `Lazy` value.
                CachedValueProvider.Result.create(
                    lazy {
                        // This code will be executed out of read action in background thread
                        if (!isUnitTestMode) checkReadAccessNotAllowed()
                        check(toolchain, project, owner, workingDirectory, packageName, isOnFly)
                    },
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            }
    }

    private fun check(
        toolchain: RustToolchain,
        project: Project,
        owner: Disposable,
        workingDirectory: Path,
        packageName: String?,
        isOnFly: Boolean
    ): RsCargoCheckAnnotationResult? = if (isOnFly) {
        val indicator = WriteAction.computeAndWait<ProgressIndicator, Throwable> {
            saveAllDocumentsAsTheyAre()
            BackgroundableProcessIndicator(
                project,
                "Analyzing Project with Cargo Check",
                PerformInBackgroundOption.ALWAYS_BACKGROUND,
                CommonBundle.getCancelButtonText(),
                CommonBundle.getCancelButtonText(),
                true
            )
        }

        ProgressManager.getInstance().runProcess(
            Computable { check(toolchain, project, owner, workingDirectory, packageName) },
            indicator
        )
    } else {
        ApplicationManager.getApplication().invokeAndWait { saveAllDocumentsAsTheyAre() }
        check(toolchain, project, owner, workingDirectory, packageName)
    }

    private fun check(
        toolchain: RustToolchain,
        project: Project,
        owner: Disposable,
        workingDirectory: Path,
        packageName: String?
    ): RsCargoCheckAnnotationResult? {
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
        return RsCargoCheckAnnotationResult(output.stdoutLines)
    }
}

fun AnnotationHolder.createAnnotationsForFile(file: RsFile, annotationResult: RsCargoCheckAnnotationResult) {
    val cargoPackageOrigin = file.containingCargoPackage?.origin
    if (cargoPackageOrigin != PackageOrigin.WORKSPACE) return

    val doc = file.viewProvider.document
        ?: error("Can't find document for $file in cargo check annotator")

    val filteredMessages = annotationResult.messages
        .mapNotNull { (topMessage) -> filterMessage(file, doc, topMessage) }
        // Cargo can duplicate some error messages when `--all-targets` attribute is used
        .distinct()
    for (message in filteredMessages) {
        // We can't control what messages cargo generates, so we can't test them well.
        // Let's use special message for tests to distinguish annotation from `RsCargoCheckUtils`
        val annotationMessage = if (isUnitTestMode) TEST_MESSAGE else message.message
        createAnnotation(message.severity, message.textRange, annotationMessage, message.htmlTooltip)
            .apply {
                problemGroup = ProblemGroup { annotationMessage }
                setNeedsUpdateOnTyping(true)
            }
    }
}

class RsCargoCheckAnnotationResult(commandOutput: List<String>) {
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

private data class RsCargoCheckFilteredMessage(
    val severity: HighlightSeverity,
    val textRange: TextRange,
    val message: String,
    val htmlTooltip: String
) {
    companion object {
        fun filterMessage(file: PsiFile, document: Document, message: RustcMessage): RsCargoCheckFilteredMessage? {
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

            return RsCargoCheckFilteredMessage(severity, textRange, message.message, tooltip)
        }
    }
}

private fun RustcSpan.isValid(): Boolean =
    line_end > line_start || (line_end == line_start && column_end >= column_start)

private fun ErrorCode?.formatAsLink(): String? =
    if (this?.code.isNullOrBlank()) null else "<a href=\"${RsConstants.ERROR_INDEX_URL}#${this?.code}\">${this?.code}</a>"

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

/**
 * Calling of [saveAllDocuments] uses [TrailingSpacesStripper] to format all unsaved documents.
 *
 * In case of [RsCargoCheckAnnotatorPass] it backfires:
 * 1. Calling [TrailingSpacesStripper.strip] on *every* file change.
 * 2. Double run of `cargo check`, because [TrailingSpacesStripper.strip] generates new "PSI change" events.
 *
 * This function saves all documents "as they are" (see [FileDocumentManager.saveDocumentAsIs]), but also fires that
 * these documents should be stripped later (when [saveAllDocuments] is called).
 */
private fun saveAllDocumentsAsTheyAre() {
    val documentManager = FileDocumentManager.getInstance()
    for (document in documentManager.unsavedDocuments) {
        documentManager.saveDocumentAsIs(document)
        documentManager.stripDocumentLater(document)
    }
}

@VisibleForTesting
fun FileDocumentManager.stripDocumentLater(document: Document): Boolean {
    if (this !is FileDocumentManagerImpl) return false
    val trailingSpacesStripper = trailingSpacesStripperField
        ?.get(this) as? TrailingSpacesStripper ?: return false
    @Suppress("UNCHECKED_CAST")
    val documentsToStripLater = documentsToStripLaterField
        ?.get(trailingSpacesStripper) as? MutableSet<Document> ?: return false
    return documentsToStripLater.add(document)
}

private val trailingSpacesStripperField: Field? =
    initFieldSafely<FileDocumentManagerImpl>("myTrailingSpacesStripper")

private val documentsToStripLaterField: Field? =
    initFieldSafely<TrailingSpacesStripper>("myDocumentsToStripLater")

private inline fun <reified T> initFieldSafely(fieldName: String): Field? =
    try {
        T::class.java
            .getDeclaredField(fieldName)
            .apply { isAccessible = true }
    } catch (e: Throwable) {
        if (isUnitTestMode) throw e else null
    }
