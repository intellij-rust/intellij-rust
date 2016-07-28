package org.rust.ide.surroundWith

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustExprElement
import java.util.*

abstract class RustExpressionSurrounderBase : Surrounder {
    abstract fun isApplicable(expression: RustExprElement): Boolean
    abstract fun surroundExpression(project: Project, editor: Editor, expression: RustExprElement): TextRange?

    final override fun isApplicable(elements: Array<out PsiElement>): Boolean {
        val expression = targetExpr(elements) ?: return false

        // TODO: Some additional filtering may be required.

        return isApplicable(expression)
    }

    final override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
        return surroundExpression(project, editor, requireNotNull(targetExpr(elements)))
    }

    private fun targetExpr(elements: Array<out PsiElement>) = elements.singleOrNull() as? RustExprElement
}
