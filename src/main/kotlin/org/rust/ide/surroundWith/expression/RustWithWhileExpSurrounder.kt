package org.rust.ide.surroundWith.expression

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustWhileExprElement
import org.rust.lang.core.types.RustBooleanType
import org.rust.lang.core.types.util.resolvedType

class RustWithWhileExpSurrounder : RustExpressionSurrounderBase<RustWhileExprElement>() {
    override fun getTemplateDescription(): String = "while expr"

    override fun createTemplate(project: Project): RustWhileExprElement =
        RustElementFactory.createExpression(project, "while a {}") as RustWhileExprElement

    override fun getLeafExpression(expression: RustWhileExprElement): RustExprElement =
        expression.expr

    override fun isApplicable(expression: RustExprElement): Boolean =
        expression.resolvedType == RustBooleanType

    override fun getSelectionRange(expression: PsiElement): TextRange? {
        val offset = (expression as RustWhileExprElement).block?.textRange?.startOffset ?: return null
        return TextRange.from(offset + 1, 0)
    }
}
