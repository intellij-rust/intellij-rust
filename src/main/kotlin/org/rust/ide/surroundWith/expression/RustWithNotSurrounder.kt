package org.rust.ide.surroundWith.expression

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.types.RustBooleanType
import org.rust.lang.core.types.util.resolvedType

class RustWithNotSurrounder : RustExpressionSurrounderBase<RustUnaryExprElement>() {
    override fun getTemplateDescription(): String = "!(expr)"

    override fun createTemplate(project: Project): RustUnaryExprElement =
        RustElementFactory.createExpression(project, "!(a)") as RustUnaryExprElement

    override fun getLeafExpression(expression: RustUnaryExprElement): RustExprElement =
        (expression.expr as RustParenExprElement).expr

    override fun isApplicable(expression: RustExprElement): Boolean =
        expression.resolvedType == RustBooleanType

    override fun getSelectionRange(expression: PsiElement): TextRange {
        val offset = expression.textRange.endOffset
        return TextRange.from(offset, 0)
    }
}
