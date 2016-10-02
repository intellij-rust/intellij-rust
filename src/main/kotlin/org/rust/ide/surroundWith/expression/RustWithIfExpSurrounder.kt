package org.rust.ide.surroundWith.expression

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustIfExprElement
import org.rust.lang.core.types.RustBooleanType
import org.rust.lang.core.types.util.resolvedType

class RustWithIfExpSurrounder : RustExpressionSurrounderBase<RustIfExprElement>() {
    override fun getTemplateDescription(): String = "if expr"

    override fun createTemplate(project: Project): RustIfExprElement =
        RustElementFactory.createExpression(project, "if a {}") as RustIfExprElement

    override fun getLeafExpression(expression: RustIfExprElement): RustExprElement =
        expression.expr

    override fun isApplicable(expression: RustExprElement): Boolean =
        expression.resolvedType == RustBooleanType

    override fun getSelectionRange(expression: PsiElement): TextRange? {
        val offset = (expression as RustIfExprElement).block?.textRange?.startOffset ?: return null
        return TextRange.from(offset + 1, 0)
    }
}
