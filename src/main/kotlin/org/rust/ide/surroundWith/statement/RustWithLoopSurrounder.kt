package org.rust.ide.surroundWith.statement

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.ide.surroundWith.statement.RustStatementsSurrounderBase
import org.rust.ide.surroundWith.addStatements
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustLoopExprElement

class RustWithLoopSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "loop { }"

    override fun surroundStatements(
        project: Project,
        editor: Editor,
        container: PsiElement,
        statements: Array<out PsiElement>
    ): TextRange? {
        var loopExpr = RustElementFactory.createExpression(project, "loop {\n}") as RustLoopExprElement
        loopExpr = container.addBefore(loopExpr, statements[0]) as RustLoopExprElement

        loopExpr.block.addStatements(statements)
        container.deleteChildRange(statements.first(), statements.last())

        val range = loopExpr.firstChild?.textRange ?: return null
        return TextRange.from(range.endOffset, 0)
    }
}
