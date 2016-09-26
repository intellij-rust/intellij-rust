package org.rust.ide.surroundWith

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustLoopExprElement

class RustWithLoopSurrounder: RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "loop { }"

    override fun surroundStatements(
        project: Project,
        editor: Editor,
        container: PsiElement,
        statements: Array<out PsiElement>
    ): TextRange? {
        var loopStmt = RustElementFactory.createExpression(project, "loop {\n}") as RustLoopExprElement
        loopStmt = container.addBefore(loopStmt, statements[0]) as RustLoopExprElement

        loopStmt.block.addStatements(statements)
        container.deleteChildRange(statements.first(), statements.last())

        val range = loopStmt.firstChild?.textRange ?: return null
        return TextRange.from(range.endOffset, 0)
    }
}
