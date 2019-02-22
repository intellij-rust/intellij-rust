/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.cargoCheck

import com.google.common.annotations.VisibleForTesting
import com.intellij.CommonBundle
import com.intellij.execution.ExecutionException
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.ProblemGroup
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.TrailingSpacesStripper
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiFile
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.annotator.cargoCheck.RsCargoCheckFilteredMessage.Companion.filterMessage
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.cargoWorkspace
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.saveAllDocuments
import java.lang.reflect.Field

object RsCargoCheckAnnotator : ExternalAnnotator<RsCargoCheckAnnotationInfo, RsCargoCheckAnnotationResult>() {
    private val LOG: Logger = Logger.getInstance(RsCargoCheckAnnotator::class.java)
    const val TEST_MESSAGE: String = "CargoCheckAnnotation"

    override fun collectInformation(file: PsiFile): RsCargoCheckAnnotationInfo? {
        if (file !is RsFile) return null
        if (!file.project.rustSettings.useCargoCheckAnnotator) return null
        val ws = file.cargoWorkspace ?: return null
        val module = ModuleUtil.findModuleForFile(file.virtualFile, file.project) ?: return null
        val toolchain = module.project.toolchain ?: return null
        val cargoPackage = file.containingCargoPackage
        if (cargoPackage?.origin != PackageOrigin.WORKSPACE) return null
        return RsCargoCheckAnnotationInfo(toolchain, ws.contentRoot, module, cargoPackage)
    }

    override fun collectInformation(
        file: PsiFile,
        editor: Editor,
        hasErrors: Boolean
    ): RsCargoCheckAnnotationInfo? = collectInformation(file)

    // NB: executed asynchronously off EDT, so care must be taken not to access disposed objects
    override fun doAnnotate(info: RsCargoCheckAnnotationInfo): RsCargoCheckAnnotationResult? {
        val indicator = WriteAction.computeAndWait<ProgressIndicator, Throwable> {
            // We have to save files to disk to give cargo a chance to check fresh file content
            saveAllDocumentsAsTheyAre()
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
            LOG.error(e)
            return null
        }

        if (output.isCancelled) return null
        return RsCargoCheckAnnotationResult(output.stdoutLines)
    }

    override fun apply(file: PsiFile, annotationResult: RsCargoCheckAnnotationResult?, holder: AnnotationHolder) {
        if (annotationResult == null) return
        val doc = file.viewProvider.document
            ?: error("Can't find document for $file in cargo check annotator")

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
}

/**
 * Calling of [saveAllDocuments] uses [TrailingSpacesStripper] to format all unsaved documents.
 *
 * In case of [RsCargoCheckAnnotator] it backfires:
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
