/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl
import com.intellij.codeInspection.ex.GlobalInspectionContextUtil
import com.intellij.codeInspection.reference.RefElement
import com.intellij.codeInspection.ui.InspectionToolPresentation
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.containers.ContainerUtil
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.impl.Applicability
import org.rust.cargo.toolchain.tools.CargoCheckArgs
import org.rust.ide.annotator.RsExternalLinterResult
import org.rust.ide.annotator.RsExternalLinterUtils
import org.rust.ide.annotator.createAnnotationsForFile
import org.rust.ide.annotator.createDisposableOnAnyPsiChange
import org.rust.lang.core.crate.asNotFake
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.stdext.buildList
import java.util.*

class RsExternalLinterInspection : GlobalSimpleInspectionTool() {

    override fun inspectionStarted(
        manager: InspectionManager,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        globalContext.putUserData(ANALYZED_FILES, ContainerUtil.newConcurrentSet())
    }

    override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        problemsHolder: ProblemsHolder,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        if (file !is RsFile || file.containingCrate.asNotFake?.origin != PackageOrigin.WORKSPACE) return
        val analyzedFiles = globalContext.getUserData(ANALYZED_FILES) ?: return
        analyzedFiles.add(file)
    }

    override fun inspectionFinished(
        manager: InspectionManager,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        if (globalContext !is GlobalInspectionContextImpl) return
        val analyzedFiles = globalContext.getUserData(ANALYZED_FILES) ?: return

        val project = manager.project
        val currentProfile = InspectionProjectProfileManager.getInstance(project).currentProfile
        val toolWrapper = currentProfile.getInspectionTool(SHORT_NAME, project) ?: return

        while (true) {
            val disposable = project.messageBus.createDisposableOnAnyPsiChange()
                .also { Disposer.register(project, it) }
            val cargoProjects = run {
                val allProjects = project.cargoProjects.allProjects
                if (allProjects.size == 1) {
                    setOf(allProjects.first())
                } else {
                    analyzedFiles.mapNotNull { it.cargoProject }.toSet()
                }
            }
            val futures = cargoProjects.map {
                ApplicationManager.getApplication().executeOnPooledThread<RsExternalLinterResult?> {
                    checkProjectLazily(it, disposable)?.value
                }
            }
            val annotationResults = futures.mapNotNull { it.get() }

            val exit = runReadAction {
                ProgressManager.checkCanceled()
                if (Disposer.isDisposed(disposable)) return@runReadAction false
                if (annotationResults.size < cargoProjects.size) return@runReadAction true
                for (annotationResult in annotationResults) {
                    val problemDescriptors = getProblemDescriptors(analyzedFiles, annotationResult)
                    val presentation = globalContext.getPresentation(toolWrapper)
                    presentation.addProblemDescriptors(problemDescriptors, globalContext)
                }
                true
            }

            if (exit) break
        }
    }

    override fun getDisplayName(): String = "External Linter"

    override fun getShortName(): String = SHORT_NAME

    companion object {
        const val SHORT_NAME: String = "RsExternalLinter"

        private val ANALYZED_FILES: Key<MutableSet<RsFile>> = Key.create("ANALYZED_FILES")

        private fun checkProjectLazily(
            cargoProject: CargoProject,
            disposable: Disposable
        ): Lazy<RsExternalLinterResult?>? = runReadAction {
            RsExternalLinterUtils.checkLazily(
                cargoProject.project.toolchain ?: return@runReadAction null,
                cargoProject.project,
                disposable,
                cargoProject.workingDirectory,
                CargoCheckArgs.forCargoProject(cargoProject)
            )
        }

        /** TODO: Use [ProblemDescriptorUtil.convertToProblemDescriptors] instead */
        private fun convertToProblemDescriptors(annotations: List<Annotation>, file: PsiFile): List<ProblemDescriptor> {
            if (annotations.isEmpty()) return emptyList()

            val problems = ArrayList<ProblemDescriptor>(annotations.size)
            val quickFixMappingCache = IdentityHashMap<IntentionAction, LocalQuickFix>()
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

            return problems
        }

        private fun toLocalQuickFixes(
            fixInfos: List<Annotation.QuickFixInfo>?,
            quickFixMappingCache: IdentityHashMap<IntentionAction, LocalQuickFix>
        ): Array<LocalQuickFix> {
            if (fixInfos.isNullOrEmpty()) return LocalQuickFix.EMPTY_ARRAY
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

        private fun getProblemDescriptors(
            analyzedFiles: Set<RsFile>,
            annotationResult: RsExternalLinterResult
        ): List<ProblemDescriptor> = buildList {
            for (file in analyzedFiles) {
                if (!file.isValid) continue
                @Suppress("UnstableApiUsage", "DEPRECATION")
                val annotationHolder = AnnotationHolderImpl(AnnotationSession(file))
                @Suppress("UnstableApiUsage")
                annotationHolder.runAnnotatorWithContext(file) { _, holder ->
                    holder.createAnnotationsForFile(file, annotationResult, Applicability.MACHINE_APPLICABLE)
                }
                addAll(convertToProblemDescriptors(annotationHolder, file))
            }
        }

        private fun InspectionToolPresentation.addProblemDescriptors(
            descriptors: List<ProblemDescriptor>,
            context: GlobalInspectionContext
        ) {
            if (descriptors.isEmpty()) return
            val problems = hashMapOf<RefElement, MutableList<ProblemDescriptor>>()

            for (descriptor in descriptors) {
                val element = descriptor.psiElement ?: continue
                val refElement = getProblemElement(element, context) ?: continue
                val elementProblems = problems.getOrPut(refElement) { mutableListOf() }
                elementProblems.add(descriptor)
            }

            for ((refElement, problemDescriptors) in problems) {
                val descriptions = problemDescriptors.toTypedArray<CommonProblemDescriptor>()
                addProblemElement(refElement, false, *descriptions)
            }
        }

        private fun getProblemElement(element: PsiElement, context: GlobalInspectionContext): RefElement? {
            val problemElement = element.ancestorOrSelf<RsFile>()
            val refElement = context.refManager.getReference(problemElement)
            return if (refElement == null && problemElement != null) {
                GlobalInspectionContextUtil.retrieveRefElement(element, context)
            } else {
                refElement
            }
        }
    }
}
