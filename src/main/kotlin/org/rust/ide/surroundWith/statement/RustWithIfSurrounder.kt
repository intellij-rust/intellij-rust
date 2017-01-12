package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsIfExpr
import org.rust.lang.core.psi.RustPsiFactory

class RustWithIfSurrounder : RustStatementsSurrounderBase.BlockWithCondition<RsIfExpr>() {
    override fun getTemplateDescription(): String = "if { }"

    override fun createTemplate(project: Project): Pair<RsIfExpr, RsBlock> {
        val i = RustPsiFactory(project).createExpression("if a {\n}") as RsIfExpr
        return i to i.block!!
    }

    override fun conditionRange(expression: RsIfExpr): TextRange =
        expression.condition!!.textRange

}
