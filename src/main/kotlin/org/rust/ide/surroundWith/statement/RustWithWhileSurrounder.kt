package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustWhileExprElement

class RustWithWhileSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "while { }"

    override fun createTemplate(project: Project): PsiElement =
        checkNotNull(RustElementFactory.createExpression(project, "while a {\n}"))

    override fun getCodeBlock(expression: PsiElement): RustBlockElement =
        checkNotNull((expression as RustWhileExprElement).block)

    override val shouldRemoveExpr = true

    override fun getExprForRemove(expression: PsiElement): PsiElement =
        (expression as RustWhileExprElement).expr
}
