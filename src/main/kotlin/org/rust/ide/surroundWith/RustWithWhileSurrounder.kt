package org.rust.ide.surroundWith

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustWhileExprElement

class RustWithWhileSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "while { }"

    override fun surroundStatements(
        project: Project,
        editor: Editor,
        container: PsiElement,
        statements: Array<out PsiElement>
    ): TextRange? {
        var whileExpr = RustElementFactory.createExpression(project, "while true {\n}") as RustWhileExprElement

        whileExpr = container.addBefore(whileExpr, statements[0]) as RustWhileExprElement
        checkNotNull(whileExpr.block).addStatements(statements)
        container.deleteChildRange(statements.first(), statements.last())

        whileExpr = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(whileExpr)

        val conditionTextRange = whileExpr.expr.textRange
        val range = TextRange.from(conditionTextRange.startOffset, 0)
        editor.document.deleteString(conditionTextRange.startOffset, conditionTextRange.endOffset)

        return range
    }
}
