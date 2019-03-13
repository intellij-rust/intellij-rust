/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.*
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.util.containers.ContainerUtil
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.ide.actions.RsRunExternalLinterAction.Companion.CARGO_PROJECT
import org.rust.ide.annotator.RsExternalLinterResult
import org.rust.ide.annotator.RsExternalLinterUtils
import org.rust.ide.annotator.createAnnotationsForFile
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.psi.isRustFile
import java.util.*

class RsExternalLinterInspection : GlobalSimpleInspectionTool() {
    override fun inspectionStarted(
        manager: InspectionManager,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        val cargoProject = findCargoProject(manager.project, globalContext) ?: return
        val annotationInfo = checkProjectLazily(cargoProject) ?: return
        // TODO: do something if the user changes project documents
        val annotationResult = annotationInfo.value ?: return
        globalContext.putUserData(ANNOTATION_RESULT, annotationResult)
    }

    override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        problemsHolder: ProblemsHolder,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        if (file !is RsFile || file.containingCargoPackage?.origin != PackageOrigin.WORKSPACE) return
        val annotationResult = globalContext.getUserData(ANNOTATION_RESULT) ?: return

        val annotationHolder = AnnotationHolderImpl(AnnotationSession(file))
        annotationHolder.createAnnotationsForFile(file, annotationResult)

        val problemDescriptors = convertToProblemDescriptors(annotationHolder, file)
        for (descriptor in problemDescriptors) {
            problemsHolder.registerProblem(descriptor)
        }
    }

    override fun getDisplayName(): String = DISPLAY_NAME

    override fun getShortName(): String = SHORT_NAME

    companion object {
        const val DISPLAY_NAME: String = "External Linter"
        const val SHORT_NAME: String = "RsExternalLinter"

        private val ANNOTATION_RESULT: Key<RsExternalLinterResult> = Key.create("ANNOTATION_RESULT")

        private fun checkProjectLazily(cargoProject: CargoProject): Lazy<RsExternalLinterResult?>? =
            runReadAction {
                RsExternalLinterUtils.checkLazily(
                    cargoProject.project.toolchain ?: return@runReadAction null,
                    cargoProject.project,
                    cargoProject.project,
                    cargoProject.workingDirectory,
                    null
                )
            }

        /** TODO: Use [ProblemDescriptorUtil.convertToProblemDescriptors] instead */
        private fun convertToProblemDescriptors(annotations: List<Annotation>, file: PsiFile): Array<ProblemDescriptor> {
            if (annotations.isEmpty()) return ProblemDescriptor.EMPTY_ARRAY

            val problems = ContainerUtil.newArrayListWithCapacity<ProblemDescriptor>(annotations.size)
            val quickFixMappingCache = ContainerUtil.newIdentityHashMap<IntentionAction, LocalQuickFix>()
            for (annotation in annotations) {
                if (annotation.severity === HighlightSeverity.INFORMATION ||
                    annotation.startOffset == annotation.endOffset &&
                    !annotation.isAfterEndOfLine) {
                    continue
                }

                val (startElement, endElement) =
                    if (annotation.startOffset == annotation.endOffset && annotation.isAfterEndOfLine) {
                        val element = file.findElementAt(annotation.endOffset - 1)
                        Pair(element, element)
                    } else {
                        Pair(file.findElementAt(annotation.startOffset), file.findElementAt(annotation.endOffset - 1))
                    }

                if (startElement == null || endElement == null) continue

                val quickFixes = toLocalQuickFixes(annotation.quickFixes, quickFixMappingCache)
                val highlightType = HighlightInfo.convertSeverityToProblemHighlight(annotation.severity)
                val descriptor = ProblemDescriptorBase(
                    startElement,
                    endElement,
                    annotation.message,
                    quickFixes,
                    highlightType,
                    annotation.isAfterEndOfLine,
                    null,
                    true,
                    false
                )
                problems.add(descriptor)
            }

            return problems.toTypedArray()
        }

        private fun toLocalQuickFixes(
            fixInfos: List<Annotation.QuickFixInfo>?,
            quickFixMappingCache: IdentityHashMap<IntentionAction, LocalQuickFix>
        ): Array<LocalQuickFix> {
            if (fixInfos == null || fixInfos.isEmpty()) return LocalQuickFix.EMPTY_ARRAY
            return fixInfos.map { fixInfo ->
                val intentionAction = fixInfo.quickFix
                if (intentionAction is LocalQuickFix) {
                    intentionAction
                } else {
                    var lqf = quickFixMappingCache[intentionAction]
                    if (lqf == null) {
                        lqf = ExternalAnnotatorInspectionVisitor.LocalQuickFixBackedByIntentionAction(intentionAction)
                        quickFixMappingCache[intentionAction] = lqf
                    }
                    lqf
                }
            }.toTypedArray()
        }
    }

    private fun findCargoProject(project: Project, globalContext: GlobalInspectionContext): CargoProject? {
        globalContext.getUserData(CARGO_PROJECT)?.let { return it }

        val cargoProjects = project.cargoProjects
        cargoProjects.allProjects.singleOrNull()?.let { return it }

        val virtualFile = FileEditorManager.getInstance(project)
            .selectedFiles.firstOrNull { it.isRustFile && it.isInLocalFileSystem }
        return virtualFile?.let { cargoProjects.findProjectForFile(it) }
    }
}
