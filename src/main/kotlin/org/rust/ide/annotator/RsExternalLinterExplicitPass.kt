/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil.setHighlightersToEditor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.toolchain.impl.Applicability
import org.rust.cargo.toolchain.tools.CargoCheckArgs
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.containingCargoTarget


class RsExternalLinterPassOnFile(
    project: Project,
    private val document: Document,
) : TextEditorHighlightingPass(project, document), DumbAware {
    @Volatile
    private var annotationInfo: RsExternalLinterResult? = null

    @Volatile
    private var disposable: Disposable = myProject

    override fun doCollectInformation(progress: ProgressIndicator) {
        val file = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return
        if (file !is RsFile) return

        val cargoTarget = file.containingCargoTarget ?: return
        if (cargoTarget.pkg.origin != PackageOrigin.WORKSPACE) return


        val moduleOrProject: Disposable = ModuleUtil.findModuleForFile(file) ?: myProject
        disposable = myProject.messageBus.createDisposableOnAnyPsiChange()
            .also { Disposer.register(moduleOrProject, it) }

        val args = CargoCheckArgs.forTarget(myProject, cargoTarget)
        annotationInfo = RsExternalLinterUtils.check(
            myProject.toolchain ?: return,
            myProject,
            disposable,
            cargoTarget.pkg.workspace.contentRoot,
            args
        )
    }

    override fun doApplyInformationToEditor() {
        val file = PsiDocumentManager.getInstance(myProject).getPsiFile(document) ?: return
        if (file !is RsFile) return

        val annotationResult = annotationInfo ?: return
        val infos = createHighlightInfo(file, annotationResult, Applicability.UNSPECIFIED)
        runReadAction {
            ProgressManager.checkCanceled()
            doFinish(file, infos)
        }
    }

    private fun doFinish(file: RsFile, highlights: List<HighlightInfo>) {
        invokeLater {
            if (Disposer.isDisposed(disposable)) return@invokeLater
            setHighlightersToEditor(
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
    }
}
