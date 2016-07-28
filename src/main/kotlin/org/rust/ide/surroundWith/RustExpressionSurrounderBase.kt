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
    abstract fun surroundExpression(project: Project, editor: Editor, expression: RustExprElement): TextRange

    final override fun isApplicable(elements: Array<out PsiElement>): Boolean {
        if (elements.size != 1 || elements[0] !is RustExprElement) {
            return false
        }

        val expression = elements[0] as RustExprElement

        // TODO: Some additional filtering may be required.

        return isApplicable(expression)
    }

    final override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange {
        require(elements.size == 1 && elements[0] is RustExprElement) {
            "RustExpressionSurrounder should be applicable only for 1 expression: ${Arrays.toString(elements)}"
        }

        return surroundExpression(project, editor, elements[0] as RustExprElement)
    }
}
