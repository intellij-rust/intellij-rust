package org.rust.ide.surroundWith.expression

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsIfExpr
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.types.RustBooleanType
import org.rust.lang.core.types.util.resolvedType

class RsWithIfExpSurrounder : RsExpressionSurrounderBase<RsIfExpr>() {
    override fun getTemplateDescription(): String = "if expr"

    override fun createTemplate(project: Project): RsIfExpr =
        RustPsiFactory(project).createExpression("if a {stmnt;}") as RsIfExpr

    override fun getWrappedExpression(expression: RsIfExpr): RsExpr =
        expression.condition!!.expr

    override fun isApplicable(expression: RsExpr): Boolean =
        expression.resolvedType == RustBooleanType

    override fun doPostprocessAndGetSelectionRange(editor: Editor, expression: PsiElement): TextRange? {
        var block = (expression as? RsIfExpr)?.block ?: return null
        block = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(block)
        val rbrace = checkNotNull(block.rbrace) {
            "Incomplete block in if surrounder"
        }

        val offset = block.lbrace.textOffset + 1
        editor.document.deleteString(offset, rbrace.textOffset)
        return TextRange.from(offset, 0)
    }
}
