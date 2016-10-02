package org.rust.ide.surroundWith.expression

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustExprElement

abstract class RustExpressionSurrounderBase<E : RustExprElement> : Surrounder {
    abstract fun createTemplate(project: Project): E
    abstract fun getLeafExpression(expression: E): RustExprElement
    abstract fun isApplicable(expression: RustExprElement): Boolean
    abstract fun getSelectionRange(expression: PsiElement): TextRange?

    final override fun isApplicable(elements: Array<out PsiElement>): Boolean {
        val expression = targetExpr(elements) ?: return false

        // TODO: Some additional filtering may be required.

        return isApplicable(expression)
    }

    final override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
        val expression = requireNotNull(targetExpr(elements))
        val templateExpr = createTemplate(project)

        getLeafExpression(templateExpr).replace(expression)
        val newExpression = expression.replace(templateExpr)

        CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newExpression)

        return getSelectionRange(newExpression)
    }

    private fun targetExpr(elements: Array<out PsiElement>) = elements.singleOrNull() as? RustExprElement
}
