/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.rust.ide.utils.isEnabledByCfg

abstract class RsLocalInspectionTool : LocalInspectionTool() {
    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        buildVisitor(RsProblemsHolder(holder), isOnTheFly) ?: super.buildVisitor(holder, isOnTheFly)

    open fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor? = null
}

class RsProblemsHolder(private val holder: ProblemsHolder) {
    val manager: InspectionManager get() = holder.manager
    val file: PsiFile get() = holder.file
    val project: Project get() = holder.project
    val isOnTheFly: Boolean get() = holder.isOnTheFly

    fun registerProblem(element: PsiElement, descriptionTemplate: String, vararg fixes: LocalQuickFix?) {
        if (element.isEnabledByCfg) {
            holder.registerProblem(element, descriptionTemplate, *fixes)
        }
    }

    fun registerProblem(
        element: PsiElement,
        descriptionTemplate: String,
        highlightType: ProblemHighlightType,
        vararg fixes: LocalQuickFix
    ) {
        if (element.isEnabledByCfg) {
            holder.registerProblem(element, descriptionTemplate, highlightType, *fixes)
        }
    }

    fun registerProblem(problemDescriptor: ProblemDescriptor) {
        if (problemDescriptor.psiElement.isEnabledByCfg) {
            holder.registerProblem(problemDescriptor)
        }
    }

    fun registerProblem(element: PsiElement, rangeInElement: TextRange, message: String, vararg fixes: LocalQuickFix?) {
        if (element.isEnabledByCfg) {
            holder.registerProblem(element, rangeInElement, message, *fixes)
        }
    }

    fun registerProblem(
        element: PsiElement,
        message: String,
        highlightType: ProblemHighlightType,
        rangeInElement: TextRange,
        vararg fixes: LocalQuickFix?
    ) {
        if (element.isEnabledByCfg) {
            holder.registerProblem(element, message, highlightType, rangeInElement, *fixes)
        }
    }
}
