package org.rust.ide.surroundWith

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockExprElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustExprStmtElement

class RustWithBlockSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "{ }"

    override fun surroundStatements(
        project: Project,
        editor: Editor,
        container: PsiElement,
        statements: Array<out PsiElement>
    ): TextRange? {
        // TODO: Move declarations out (tricky part: borrowing)

        var blockStmt = RustElementFactory.createStatement(project, "{\n}") as RustExprStmtElement
        blockStmt = container.addBefore(blockStmt, statements[0]) as RustExprStmtElement

        val block = (blockStmt.expr as RustBlockExprElement).block!!
        block.addStatements(statements)
        container.deleteChildRange(statements.first(), statements.last())

        val range = blockStmt.firstChild?.textRange ?: return null
        return TextRange.from(range.endOffset, 0)
    }
}
