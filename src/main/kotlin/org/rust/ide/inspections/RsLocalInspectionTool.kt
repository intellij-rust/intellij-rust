/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.*
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.rust.cargo.project.settings.toolchain
import org.rust.ide.fixes.RsQuickFixBase
import org.rust.lang.core.macros.findElementExpandedFrom
import org.rust.lang.core.macros.findMacroCallExpandedFromNonRecursive
import org.rust.lang.core.macros.mapRangeFromExpansionToCallBody
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.existsAfterExpansion
import org.rust.lang.core.psi.ext.startOffset
import org.rust.openapiext.isUnitTestMode

abstract class RsLocalInspectionTool : LocalInspectionTool() {
    final override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        val file = session.file
        return if (file is RsFile && isApplicableTo(file)) {
            buildVisitor(holder, isOnTheFly)
        } else {
            PsiElementVisitor.EMPTY_VISITOR
        }
    }

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        buildVisitor(RsProblemsHolder(holder), isOnTheFly) ?: super.buildVisitor(holder, isOnTheFly)

    open fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor? = null

    open val isSyntaxOnly: Boolean = false

    /**
     * Syntax-only inspections are applicable to any [RsFile].
     *
     * Other inspections should analyze only files that:
     * - belong to a workspace
     * - are included in module tree, i.e. have a crate root
     * - are not disabled with a `cfg` attribute
     * - belong to a project with a configured and valid Rust toolchain
     */
    private fun isApplicableTo(file: RsFile): Boolean {
        if (isSyntaxOnly) return true
        if (!file.isDeeplyEnabledByCfg) return false

        if (isUnitTestMode) return true

        return file.cargoWorkspace != null
            && file.crateRoot != null
            && file.project.toolchain != null
    }
}

class RsProblemsHolder(private val holder: ProblemsHolder) {
    val manager: InspectionManager get() = holder.manager
    val file: PsiFile get() = holder.file
    val project: Project get() = holder.project
    val isOnTheFly: Boolean get() = holder.isOnTheFly

    fun registerProblem(element: PsiElement, @InspectionMessage descriptionTemplate: String, vararg fixes: LocalQuickFix) {
        if (element.existsAfterExpansion) {
            if (element.containingFile == file) {
                holder.registerProblem(element, descriptionTemplate, *fixes)
            } else {
                // The element is expanded from a macro
                val (sourceElement, sourceRangeInElement) = element.findCorrespondingElementAndRangeExpandedFrom()
                    ?: return
                holder.registerProblem(
                    sourceElement,
                    sourceRangeInElement,
                    descriptionTemplate,
                    *fixes.filterSupportingMacros()
                )
            }
        }
    }

    fun registerProblem(
        element: PsiElement,
        @InspectionMessage descriptionTemplate: String,
        highlightType: ProblemHighlightType,
        vararg fixes: LocalQuickFix
    ) {
        if (element.existsAfterExpansion && isProblemWithTypeAllowed(highlightType)) {
            if (element.containingFile == file) {
                holder.registerProblem(element, descriptionTemplate, highlightType, *fixes)
            } else {
                // The element is expanded from a macro
                val (sourceElement, sourceRangeInElement) = element.findCorrespondingElementAndRangeExpandedFrom()
                    ?: return
                holder.registerProblem(
                    sourceElement,
                    descriptionTemplate,
                    highlightType,
                    sourceRangeInElement,
                    *fixes.filterSupportingMacros()
                )
            }
        }
    }

    fun registerProblem(
        startElement: PsiElement,
        endElement: PsiElement,
        descriptionTemplate: @InspectionMessage String,
        highlightType: ProblemHighlightType,
        vararg fixes: LocalQuickFix,
    ) {
        if (startElement == endElement) {
            registerProblem(startElement, descriptionTemplate, highlightType, *fixes)
            return
        }
        if (startElement.existsAfterExpansion && isProblemWithTypeAllowed(highlightType)) {
            val descriptor = if (startElement.containingFile == file) {
                holder.manager.createProblemDescriptor(
                    startElement,
                    endElement,
                    descriptionTemplate,
                    highlightType,
                    holder.isOnTheFly,
                    *fixes,
                )
            } else {
                // The element is expanded from a macro
                val sourceStartElement = startElement.findCorrespondingElementExpandedFrom() ?: return
                val sourceEndElement = endElement.findCorrespondingElementExpandedFrom() ?: return
                holder.manager.createProblemDescriptor(
                    sourceStartElement,
                    sourceEndElement,
                    descriptionTemplate,
                    highlightType,
                    holder.isOnTheFly,
                    *fixes.filterSupportingMacros()
                )
            }
            holder.registerProblem(descriptor)
        }
    }

    fun registerProblem(
        element: PsiElement,
        rangeInElement: TextRange,
        @InspectionMessage message: String,
        vararg fixes: LocalQuickFix,
        // A temporary hack; should actually always be true
        alwaysShowInMacros: Boolean = false
    ) {
        if (element.existsAfterExpansion) {
            if (element.containingFile == file) {
                holder.registerProblem(element, rangeInElement, message, *fixes)
            } else {
                // The element is expanded from a macro
                val (sourceElement, sourceRangeInElement) =
                    element.findCorrespondingElementAndRangeExpandedFrom(rangeInElement) ?: run {
                        if (alwaysShowInMacros) {
                            val macroPath = element.findMacroCallExpandedFromNonRecursive()?.path ?: return
                            registerProblem(
                                macroPath,
                                TextRange(0, macroPath.textLength),
                                message,
                                *fixes,
                                alwaysShowInMacros = true
                            )
                        }
                        return
                    }
                holder.registerProblem(sourceElement, sourceRangeInElement, message, *fixes.filterSupportingMacros())
            }
        }
    }

    fun registerProblem(
        element: PsiElement,
        @InspectionMessage message: String,
        highlightType: ProblemHighlightType,
        rangeInElement: TextRange,
        vararg fixes: LocalQuickFix
    ) {
        if (element.existsAfterExpansion && isProblemWithTypeAllowed(highlightType)) {
            if (element.containingFile == file) {
                holder.registerProblem(element, message, highlightType, rangeInElement, *fixes)
            } else {
                // The element is expanded from a macro
                val (sourceElement, sourceRangeInElement) =
                    element.findCorrespondingElementAndRangeExpandedFrom(rangeInElement) ?: return
                holder.registerProblem(
                    sourceElement,
                    message,
                    highlightType,
                    sourceRangeInElement,
                    *fixes.filterSupportingMacros()
                )
            }
        }
    }

    private fun isProblemWithTypeAllowed(highlightType: ProblemHighlightType): Boolean =
        highlightType != ProblemHighlightType.INFORMATION || holder.isOnTheFly

    private fun PsiElement.findCorrespondingElementExpandedFrom(): PsiElement? {
        val leaf = findElementExpandedFrom() ?: return null
        val textLength = textLength
        val elementType = elementType
        return leaf.ancestors.find { it.textLength == textLength && it.elementType == elementType }
    }

    private fun PsiElement.findCorrespondingElementAndRangeExpandedFrom(
        rangeInElement: TextRange = TextRange(0, textLength)
    ): Pair<PsiElement, TextRange>? {
        // TODO simplify: map the leaf and the text range at once
        val macroCall = findMacroCallExpandedFromNonRecursive() ?: return null
        val leaf = findElementExpandedFrom() ?: return null
        val sourceRange = macroCall.mapRangeFromExpansionToCallBody(rangeInElement.shiftRight(startOffset))
            .singleOrNull()
            ?: return null
        val sourceElement = leaf.ancestors.find { it.textRange.contains(sourceRange) }
            ?: return null
        return sourceElement to sourceRange.shiftLeft(sourceElement.startOffset)
    }

    private fun Array<out LocalQuickFix>.filterSupportingMacros(): Array<LocalQuickFix> {
        if (!isOnTheFly) {
            // Quick fixes in macros does not allowed in batch mode for now
            return LocalQuickFix.EMPTY_ARRAY
        }
        return filterIsInstance<RsQuickFixBase<*>>().toTypedArray()
    }
}
