package org.rust.ide.surroundWith.statement

import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.ide.surroundWith.statement.RustStatementsSurrounderBase
import org.rust.ide.surroundWith.addStatements
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustForExprElement

class RustWithForSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "for { }"

    override fun surroundStatements(
        project: Project,
        editor: Editor,
        container: PsiElement,
        statements: Array<out PsiElement>
    ): TextRange? {
        var forExpr = RustElementFactory.createExpression(project, "for a in b {\n}") as RustForExprElement

        forExpr = container.addBefore(forExpr, statements[0]) as RustForExprElement
        forExpr.block.addStatements(statements)
        container.deleteChildRange(statements.first(), statements.last())

        forExpr = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(forExpr)

        val conditionTextRange = forExpr.scopedForDecl.textRange
        val range = TextRange.from(conditionTextRange.startOffset, 0)
        editor.document.deleteString(conditionTextRange.startOffset, conditionTextRange.endOffset)

        return range
    }
}
