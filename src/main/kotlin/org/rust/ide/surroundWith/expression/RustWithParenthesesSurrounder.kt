package org.rust.ide.surroundWith.expression

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustParenExprElement

class RustWithParenthesesSurrounder : RustExpressionSurrounderBase<RustParenExprElement>() {
    override fun getTemplateDescription(): String = "(expr)"

    override fun createTemplate(project: Project): RustParenExprElement =
        RustElementFactory.createExpression(project, "(a)") as RustParenExprElement

    override fun getLeafExpression(expression: RustParenExprElement): RustExprElement =
        expression.expr

    override fun isApplicable(expression: RustExprElement): Boolean = true

    override fun getSelectionRange(expression: PsiElement): TextRange {
        val offset = expression.textRange.endOffset
        return TextRange.from(offset, 0)
    }
}
