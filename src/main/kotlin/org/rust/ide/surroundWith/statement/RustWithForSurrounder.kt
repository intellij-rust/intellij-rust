package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustForExprElement
import org.rust.lang.core.psi.RustPsiFactory

class RustWithForSurrounder : RustStatementsSurrounderBase.BlockWithCondition<RustForExprElement>() {
    override fun getTemplateDescription(): String = "for { }"

    override fun createTemplate(project: Project): Pair<RustForExprElement, RustBlockElement> {
        val f = RustPsiFactory(project).createExpression("for a in b {\n}") as RustForExprElement
        return f to f.block
    }

    override fun conditionRange(expression: RustForExprElement): TextRange =
        expression.scopedForDecl.textRange
}
