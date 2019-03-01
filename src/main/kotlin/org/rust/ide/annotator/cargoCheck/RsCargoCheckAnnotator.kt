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
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.annotator.cargoCheck.RsCargoCheckFilteredMessage.Companion.filterMessage
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.checkReadAccessNotAllowed
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.saveAllDocuments
import java.lang.reflect.Field

object RsCargoCheckAnnotator : ExternalAnnotator<Lazy<RsCargoCheckAnnotationResult?>, RsCargoCheckAnnotationResult>() {
    private val LOG: Logger = Logger.getInstance(RsCargoCheckAnnotator::class.java)
    const val TEST_MESSAGE: String = "CargoCheckAnnotation"

    /**
     * Returns (and cached if absent) lazily computed `cargo check` result.
     *
     * Note: before applying this result you need to check that the PSI modification stamp of current project has not
     * changed after calling this method.
     *
     * @see PsiModificationTracker.MODIFICATION_COUNT
     */
    override fun collectInformation(file: PsiFile): Lazy<RsCargoCheckAnnotationResult?>? {
        checkReadAccessAllowed()
        if (file !is RsFile) return null
        val project = file.project
        if (!project.rustSettings.useCargoCheckAnnotator) return null

        val cargoPackage = file.containingCargoPackage
        if (cargoPackage?.origin != PackageOrigin.WORKSPACE) return null

        val info = RsCargoCheckAnnotationInfo(
            project.toolchain ?: return null,
            project,
            ModuleUtil.findModuleForFile(file) ?: project,
            cargoPackage.workspace.contentRoot,
            cargoPackage.name
        )

        return CachedValuesManager.getManager(project)
            .getCachedValue(info.owner) {
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
                        checkProject(info)
                    },
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            }
    }

    override fun collectInformation(
        file: PsiFile,
        editor: Editor,
        hasErrors: Boolean
    ): Lazy<RsCargoCheckAnnotationResult?>? = collectInformation(file)

    override fun doAnnotate(result: Lazy<RsCargoCheckAnnotationResult?>): RsCargoCheckAnnotationResult? {
        if (!isUnitTestMode) checkReadAccessNotAllowed()
        return result.value
    }

    // NB: executed asynchronously off EDT, so care must be taken not to access disposed objects
    private fun checkProject(info: RsCargoCheckAnnotationInfo): RsCargoCheckAnnotationResult? {
        val indicator = WriteAction.computeAndWait<ProgressIndicator, Throwable> {
            // We have to save files to disk to give cargo a chance to check fresh file content
            saveAllDocumentsAsTheyAre()
            BackgroundableProcessIndicator(
                info.project,
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
                    .cargoOrWrapper(info.workingDirectory)
                    .checkProject(info.project, info.owner, info.workingDirectory, info.packageName)
            }, indicator)
        } catch (e: ExecutionException) {
            LOG.error(e)
            return null
        }

        if (output.isCancelled) return null
        return RsCargoCheckAnnotationResult(output.stdoutLines)
    }

    override fun apply(file: PsiFile, annotationResult: RsCargoCheckAnnotationResult?, holder: AnnotationHolder) {
        if (file !is RsFile) return
        if (annotationResult == null) return

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
