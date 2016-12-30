package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.RustWhileExprElement

class RustWithWhileSurrounder : RustStatementsSurrounderBase.BlockWithCondition<RustWhileExprElement>() {
    override fun getTemplateDescription(): String = "while { }"

    override fun createTemplate(project: Project): Pair<RustWhileExprElement, RustBlockElement> {
        val w = RustPsiFactory(project).createExpression("while a {\n}") as RustWhileExprElement
        return w to w.block!!
    }

    override fun conditionRange(expression: RustWhileExprElement): TextRange =
        expression.condition!!.textRange
}
