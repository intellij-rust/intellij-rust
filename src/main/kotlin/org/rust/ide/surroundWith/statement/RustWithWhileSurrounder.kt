package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsWhileExpr
import org.rust.lang.core.psi.RustPsiFactory

class RustWithWhileSurrounder : RustStatementsSurrounderBase.BlockWithCondition<RsWhileExpr>() {
    override fun getTemplateDescription(): String = "while { }"

    override fun createTemplate(project: Project): Pair<RsWhileExpr, RsBlock> {
        val w = RustPsiFactory(project).createExpression("while a {\n}") as RsWhileExpr
        return w to w.block!!
    }

    override fun conditionRange(expression: RsWhileExpr): TextRange =
        expression.condition!!.textRange
}
