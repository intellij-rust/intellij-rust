package org.rust.ide.surroundWith

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustParenExprElement
import org.rust.lang.core.psi.RustUnaryExprElement
import org.rust.lang.core.types.RustBooleanType
import org.rust.lang.core.types.util.resolvedType

class RustWithNotSurrounder : RustExpressionSurrounderBase() {
    override fun getTemplateDescription(): String = "!(expr)"

    override fun isApplicable(expression: RustExprElement): Boolean =
        expression.resolvedType == RustBooleanType

    override fun surroundExpression(project: Project, editor: Editor, expression: RustExprElement): TextRange {
        val unaryExpr = RustElementFactory.createExpression(project, "!(a)") as RustUnaryExprElement
        val parenExpr = unaryExpr.expr as RustParenExprElement
        parenExpr.expr.replace(expression)

        val newExpression = expression.replace(unaryExpr)

        CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newExpression)

        val offset = newExpression.textRange.endOffset
        return TextRange.from(offset, 0)
    }
}
