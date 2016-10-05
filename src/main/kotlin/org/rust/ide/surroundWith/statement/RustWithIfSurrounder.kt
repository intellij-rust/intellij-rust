package org.rust.ide.surroundWith.statement

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustIfExprElement

class RustWithIfSurrounder : RustStatementsSurrounderBase() {
    override fun getTemplateDescription(): String = "if { }"

    override fun createTemplate(project: Project): PsiElement =
        checkNotNull(RustElementFactory.createExpression(project, "if a {\n}"))

    override fun getCodeBlock(expression: PsiElement): RustBlockElement =
        checkNotNull((expression as RustIfExprElement).block)

    override val shouldRemoveExpr = true

    override fun getExprForRemove(expression: PsiElement): PsiElement =
        (expression as RustIfExprElement).expr
}
