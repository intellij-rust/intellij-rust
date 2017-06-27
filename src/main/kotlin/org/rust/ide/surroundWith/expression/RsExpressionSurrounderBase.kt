/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.expression

import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExpr

abstract class RsExpressionSurrounderBase<E : RsExpr> : Surrounder {
    abstract fun createTemplate(project: Project): E
    abstract fun getWrappedExpression(expression: E): RsExpr
    abstract fun isApplicable(expression: RsExpr): Boolean
    abstract fun doPostprocessAndGetSelectionRange(editor: Editor, expression: PsiElement): TextRange?

    final override fun isApplicable(elements: Array<out PsiElement>): Boolean {
        val expression = targetExpr(elements) ?: return false
        return isApplicable(expression)
    }

    final override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
        val expression = requireNotNull(targetExpr(elements))
        val templateExpr = createTemplate(project)

        getWrappedExpression(templateExpr).replace(expression)
        val newExpression = expression.replace(templateExpr)

        return doPostprocessAndGetSelectionRange(editor, newExpression)
    }

    private fun targetExpr(elements: Array<out PsiElement>) = elements.singleOrNull() as? RsExpr
}
