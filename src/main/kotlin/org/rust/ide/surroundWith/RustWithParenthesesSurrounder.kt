package org.rust.ide.surroundWith

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustParenExprElement

class RustWithParenthesesSurrounder : RustExpressionSurrounderBase() {
    override fun getTemplateDescription(): String = "(expr)"

    override fun isApplicable(expression: RustExprElement): Boolean = true

    override fun surroundExpression(project: Project, editor: Editor, expression: RustExprElement): TextRange {
        val parenExpr = RustElementFactory.createExpression(project, "(a)") as RustParenExprElement
        parenExpr.expr.replace(expression)

        val newExpression = expression.replace(parenExpr)

        CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(newExpression)

        val offset = newExpression.textRange.endOffset
        return TextRange.from(offset, 0)
    }
}
