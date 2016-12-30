package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustLoopExprElement
import org.rust.lang.core.psi.RustPsiFactory

class RustWithLoopSurrounder : RustStatementsSurrounderBase.SimpleBlock<RustLoopExprElement>() {
    override fun getTemplateDescription(): String = "loop { }"

    override fun createTemplate(project: Project): Pair<RustLoopExprElement, RustBlockElement> {
        val l = RustPsiFactory(project).createExpression("loop {\n}") as RustLoopExprElement
        return l to l.block
    }

}
