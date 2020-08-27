/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.inlineValue

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.refactoring.RefactoringBundle
import org.rust.ide.refactoring.RsInlineDialog

class RsInlineValueDialog(
    private val context: InlineValueContext,
    project: Project = context.element.project,
    occurrencesNumber: Int = initOccurrencesNumber(context.element)
) : RsInlineDialog(context.element, context.reference, project, occurrencesNumber) {
    init {
        init()
    }

    override fun doAction() {
        invokeRefactoring(getProcessor())
    }

    private fun getProcessor(): RsInlineValueProcessor {
        val mode = when {
            isInlineThisOnly -> InlineValueMode.InlineThisOnly
            isKeepTheDeclaration -> InlineValueMode.InlineAllAndKeepOriginal
            else -> InlineValueMode.InlineAllAndRemoveOriginal
        }

        return RsInlineValueProcessor(project, context, mode)
    }

    override fun getBorderTitle(): String =
        RefactoringBundle.message("inline.field.border.title")

    override fun getLabelText(occurrences: String): String {
        return "${context.type.capitalize()} ${context.name} $occurrences"
    }

    override fun getInlineAllText(): String {
        val text =
            if (context.element.isWritable) {
                "all.references.and.remove.the.local"
            } else {
                "all.invocations.in.project"
            }
        return RefactoringBundle.message(text)
    }

    override fun getInlineThisText(): String =
        "Inline this only and keep the ${context.type}"

    override fun getKeepTheDeclarationText(): String =
        if (context.element.isWritable) {
            "Inline all references and keep the ${context.type}"
        } else {
            super.getKeepTheDeclarationText()
        }

    override fun getHelpId(): String = "refactoring.inlineVariable"
}

val InlineValueContext.type: String
    get() = when (this) {
        is InlineValueContext.Variable -> "variable"
        is InlineValueContext.Constant -> "constant"
    }
val InlineValueContext.name: String
    get() = this.element.name ?: ""
