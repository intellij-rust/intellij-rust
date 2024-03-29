/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.expression

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsParenExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.endOffset

class RsWithParenthesesSurrounder : RsExpressionSurrounderBase<RsParenExpr>() {

    @Suppress("DialogTitleCapitalization")
    override fun getTemplateDescription(): String = RsBundle.message("action.expr.text")

    override fun createTemplate(project: Project): RsParenExpr =
        RsPsiFactory(project).createExpression("(a)") as RsParenExpr

    override fun getWrappedExpression(expression: RsParenExpr): RsExpr =
        expression.expr!!

    override fun isApplicable(expression: RsExpr): Boolean = true

    override fun doPostprocessAndGetSelectionRange(editor: Editor, expression: PsiElement): TextRange {
        val offset = expression.endOffset
        return TextRange.from(offset, 0)
    }
}
