package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustLoopExprElement

class RustWithLoopSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "loop { }"

    override fun createTemplate(project: Project): PsiElement =
        checkNotNull(RustElementFactory.createExpression(project, "loop {\n}"))

    override fun getCodeBlock(expression: PsiElement): RustBlockElement =
        (expression as RustLoopExprElement).block
}
