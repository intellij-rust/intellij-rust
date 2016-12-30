package org.rust.ide.surroundWith.statement

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.ide.surroundWith.addStatements
import org.rust.lang.core.psi.RustBlockElement

abstract class RustStatementsSurrounderBase : Surrounder {
    protected abstract fun createTemplate(project: Project): PsiElement
    protected abstract fun getCodeBlock(expression: PsiElement): RustBlockElement

    protected open fun getExprForRemove(expression: PsiElement): PsiElement? = null
    protected open val shouldRemoveExpr = false

    final override fun isApplicable(elements: Array<out PsiElement>): Boolean =
        elements.isNotEmpty()

    final override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
        require(elements.isNotEmpty())
        val container = requireNotNull(elements[0].parent)

        var template = createTemplate(project)
        template = container.addBefore(template, elements[0])

        getCodeBlock(template).addStatements(elements)

        container.deleteChildRange(elements.first(), elements.last())

        val newCaretOffset = if (shouldRemoveExpr) {
            template = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(template)
            val removeIt = getExprForRemove(template)
            val conditionTextRange = checkNotNull(removeIt).textRange
            editor.document.deleteString(conditionTextRange.startOffset, conditionTextRange.endOffset)
            conditionTextRange.startOffset
        } else {
            template.firstChild.textRange.endOffset
        }

        return TextRange.from(newCaretOffset, 0)
    }
}
