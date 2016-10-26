package org.rust.ide.surroundWith.expression

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.openapi.editor.Editor
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
        RustElementFactory.createExpression(project, "if a {stmnt;}") as RustIfExprElement

    override fun getWrappedExpression(expression: RustIfExprElement): RustExprElement =
        expression.expr

    override fun isApplicable(expression: RustExprElement): Boolean =
        expression.resolvedType == RustBooleanType

    override fun doPostprocessAndGetSelectionRange(editor: Editor, expression: PsiElement): TextRange? {
        var block = (expression as? RustIfExprElement)?.block ?: return null
        block = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(block)

        val offset = block.lbrace.textOffset + 1
        editor.document.deleteString(offset, block.rbrace.textOffset)
        return TextRange.from(offset, 0)
    }
}
