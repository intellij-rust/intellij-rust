package org.rust.ide.surroundWith

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustIfExprElement

class RustWithIfSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "if { }"

    override fun surroundStatements(
        project: Project,
        editor: Editor,
        container: PsiElement,
        statements: Array<out PsiElement>
    ): TextRange? {
        var ifExpr = RustElementFactory.createExpression(project, "if true {\n}") as RustIfExprElement
        
        ifExpr = container.addBefore(ifExpr, statements[0]) as RustIfExprElement
        checkNotNull(ifExpr.block).addStatements(statements)
        container.deleteChildRange(statements.first(), statements.last())

        ifExpr = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(ifExpr)

        val conditionTextRange = ifExpr.expr.textRange
        val range = TextRange.from(conditionTextRange.startOffset, 0)
        editor.document.deleteString(conditionTextRange.startOffset, conditionTextRange.endOffset)

        return range
    }
}
