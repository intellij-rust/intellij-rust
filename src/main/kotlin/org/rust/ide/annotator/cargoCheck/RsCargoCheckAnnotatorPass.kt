/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.cargoCheck

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.*
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update

class RsCargoCheckAnnotatorPass(
    private val factory: RsCargoCheckAnnotatorPassFactory,
    private val file: PsiFile,
    private val editor: Editor
) : TextEditorHighlightingPass(
    file.project,
    editor.document
) {
    private val annotationHolder: AnnotationHolderImpl = AnnotationHolderImpl(AnnotationSession(file))
    private var annotationInfo: RsCargoCheckAnnotationInfo? = null
    private var annotationResult: RsCargoCheckAnnotationResult? = null

    override fun doCollectInformation(progress: ProgressIndicator) {
        annotationHolder.clear()
        annotationInfo = try {
            RsCargoCheckAnnotator.collectInformation(file, editor, false)
        } catch (t: Throwable) {
            process(t)
            null
        }
    }

    override fun doApplyInformationToEditor() {
        val modificationStampBefore = myDocument?.modificationStamp ?: return

        val update = object : Update(file) {
            override fun setRejected() {
                super.setRejected()
                doFinish(getHighlights(), modificationStampBefore)
            }

            override fun run() {
                if (!documentChanged(modificationStampBefore) && !myProject.isDisposed) {
                    BackgroundTaskUtil.runUnderDisposeAwareIndicator(myProject, Runnable {
                        doAnnotate()
                        ReadAction.run<RuntimeException> {
                            ProgressManager.checkCanceled()
                            if (!documentChanged(modificationStampBefore)) {
                                doApply()
                                doFinish(getHighlights(), modificationStampBefore)
                            }
                        }
                    })
                }
            }
        }

        factory.scheduleExternalActivity(update)
    }

    private fun doAnnotate() {
        val dumbService = DumbService.getInstance(myProject)
        if (dumbService.isDumb && !DumbService.isDumbAware(RsCargoCheckAnnotator)) return
        try {
            annotationResult = RsCargoCheckAnnotator.doAnnotate(annotationInfo ?: return)
        } catch (t: Throwable) {
            process(t)
        }
    }

    private fun doApply() {
        if (annotationResult == null || !file.isValid) return
        try {
            RsCargoCheckAnnotator.apply(file, annotationResult, annotationHolder)
        } catch (t: Throwable) {
            process(t)
        }
    }

    private fun doFinish(highlights: List<HighlightInfo>, modificationStampBefore: Long) {
        val document = document ?: return
        ApplicationManager.getApplication().invokeLater({
            if (!documentChanged(modificationStampBefore) && !myProject.isDisposed) {
                UpdateHighlightersUtil.setHighlightersToEditor(
                    myProject,
                    document,
                    0,
                    file.textLength,
                    highlights,
                    colorsScheme,
                    id
                )
                DaemonCodeAnalyzerEx.getInstanceEx(myProject).fileStatusMap.markFileUpToDate(document, id)
            }
        }, ModalityState.stateForComponent(editor.component))
    }

    private fun getHighlights(): List<HighlightInfo> =
        annotationHolder.map(HighlightInfo::fromAnnotation)

    private fun documentChanged(modificationStampBefore: Long): Boolean =
        myDocument?.modificationStamp != modificationStampBefore

    companion object {
        private val LOG: Logger = Logger.getInstance(RsCargoCheckAnnotatorPass::class.java)

        private fun process(t: Throwable) {
            if (t is ProcessCanceledException) throw t
            LOG.error(t)
        }
    }
}

class RsCargoCheckAnnotatorPassFactory(
    project: Project,
    registrar: TextEditorHighlightingPassRegistrar
) : DirtyScopeTrackingHighlightingPassFactory {
    private val myPassId: Int = registrar.registerTextEditorHighlightingPass(
        this,
        null,
        null,
        false,
        -1
    )

    private val cargoCheckQueue = MergingUpdateQueue(
        "CargoCheckQueue",
        TIME_SPAN,
        true,
        MergingUpdateQueue.ANY_COMPONENT,
        project,
        null,
        false
    )

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        FileStatusMap.getDirtyTextRange(editor, passId) ?: return null
        return RsCargoCheckAnnotatorPass(this, file, editor)
    }

    override fun getPassId(): Int = myPassId

    fun scheduleExternalActivity(update: Update) = cargoCheckQueue.queue(update)

    companion object {
        private const val TIME_SPAN: Int = 300
    }
}
